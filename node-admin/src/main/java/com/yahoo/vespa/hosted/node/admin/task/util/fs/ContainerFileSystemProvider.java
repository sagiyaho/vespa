// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.fs;

import com.yahoo.vespa.hosted.node.admin.nodeagent.UserNamespace;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerAttributeViews.ContainerPosixFileAttributes;
import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerAttributeViews.ContainerPosixFileAttributeView;
import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerUserPrincipalLookupService.ContainerGroupPrincipal;
import static com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerUserPrincipalLookupService.ContainerUserPrincipal;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author valerijf
 */
class ContainerFileSystemProvider extends FileSystemProvider {
    private final ContainerFileSystem containerFs;
    private final ContainerUserPrincipalLookupService userPrincipalLookupService;
    private final Path containerRootOnHost;

    ContainerFileSystemProvider(Path containerRootOnHost, UserNamespace userNamespace) {
        this.containerFs = new ContainerFileSystem(this);
        this.userPrincipalLookupService = new ContainerUserPrincipalLookupService(
                containerRootOnHost.getFileSystem().getUserPrincipalLookupService(), userNamespace);
        this.containerRootOnHost = containerRootOnHost;
    }

    public Path containerRootOnHost() {
        return containerRootOnHost;
    }

    public ContainerUserPrincipalLookupService userPrincipalLookupService() {
        return userPrincipalLookupService;
    }

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        throw new FileSystemAlreadyExistsException();
    }

    @Override
    public ContainerFileSystem getFileSystem(URI uri) {
        return containerFs;
    }

    @Override
    public Path getPath(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        Path pathOnHost = pathOnHost(path);
        boolean existedBefore = Files.exists(pathOnHost);
        SeekableByteChannel seekableByteChannel = provider(pathOnHost).newByteChannel(pathOnHost, options, attrs);
        if (!existedBefore) fixOwnerToContainerRoot(toContainerPath(path));
        return seekableByteChannel;
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        Path pathOnHost = pathOnHost(dir);
        return new ContainerDirectoryStream(provider(pathOnHost).newDirectoryStream(pathOnHost, filter));
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        Path pathOnHost = pathOnHost(dir);
        boolean existedBefore = Files.exists(pathOnHost);
        provider(pathOnHost).createDirectory(pathOnHost);
        if (!existedBefore) fixOwnerToContainerRoot(toContainerPath(dir));
    }

    @Override
    public void delete(Path path) throws IOException {
        Path pathOnHost = pathOnHost(path);
        provider(pathOnHost).delete(pathOnHost);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        // Only called when both 'source' and 'target' have 'this' as the FS provider
        Path targetPathOnHost = pathOnHost(target);
        provider(targetPathOnHost).copy(pathOnHost(source), targetPathOnHost, options);
        fixOwnerToContainerRoot(toContainerPath(target));
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        // Only called when both 'source' and 'target' have 'this' as the FS provider
        Path targetPathOnHost = pathOnHost(target);
        provider(targetPathOnHost).move(pathOnHost(source), targetPathOnHost, options);
        fixOwnerToContainerRoot(toContainerPath(target));
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        Path pathOnHost = pathOnHost(link);
        if (target instanceof ContainerPath)
            target = pathOnHost.getFileSystem().getPath(toContainerPath(target).pathInContainer());
        provider(pathOnHost).createSymbolicLink(pathOnHost, target, attrs);
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        Path pathOnHost = pathOnHost(link);
        return provider(pathOnHost).readSymbolicLink(pathOnHost);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        // 'path' FS provider should be 'this'
        if (path2 instanceof ContainerPath)
            path2 = pathOnHost(path2);
        Path pathOnHost = pathOnHost(path);
        return provider(pathOnHost).isSameFile(pathOnHost, path2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        Path pathOnHost = pathOnHost(path);
        return provider(pathOnHost).isHidden(pathOnHost);
    }

    @Override
    public FileStore getFileStore(Path path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        Path pathOnHost = pathOnHost(path);
        provider(pathOnHost).checkAccess(pathOnHost, modes);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        if (!type.isAssignableFrom(PosixFileAttributeView.class)) return null;
        Path pathOnHost = pathOnHost(path);
        FileSystemProvider provider = pathOnHost.getFileSystem().provider();
        if (type == BasicFileAttributeView.class) // Basic view doesnt have owner/group fields, forward to base FS provider
            return provider.getFileAttributeView(pathOnHost, type, options);

        PosixFileAttributeView view = provider.getFileAttributeView(pathOnHost, PosixFileAttributeView.class, options);
        return (V) new ContainerPosixFileAttributeView(view,
                uncheck(() -> new ContainerPosixFileAttributes(readAttributes(path, "unix:*", options))));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (!type.isAssignableFrom(PosixFileAttributes.class)) throw new UnsupportedOperationException();
        Path pathOnHost = pathOnHost(path);
        if (type == BasicFileAttributes.class)
            return pathOnHost.getFileSystem().provider().readAttributes(pathOnHost, type, options);

        // Non-basic requests need to be upgraded to unix:* to get owner,group,uid,gid fields, which are then re-mapped
        return (A) new ContainerPosixFileAttributes(readAttributes(path, "unix:*", options));
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        Path pathOnHost = pathOnHost(path);
        int index = attributes.indexOf(':');
        if (index < 0 || attributes.startsWith("basic:"))
            return provider(pathOnHost).readAttributes(pathOnHost, attributes, options);

        Map<String, Object> attrs = new HashMap<>(provider(pathOnHost).readAttributes(pathOnHost, "unix:*", options));
        int uid = userPrincipalLookupService.userIdInContainer((int) attrs.get("uid"));
        int gid = userPrincipalLookupService.groupIdInContainer((int) attrs.get("gid"));
        attrs.put("uid", uid);
        attrs.put("gid", gid);
        attrs.put("owner", userPrincipalLookupService.userPrincipal(uid, (UserPrincipal) attrs.get("owner")));
        attrs.put("group", userPrincipalLookupService.groupPrincipal(gid, (GroupPrincipal) attrs.get("group")));
        return attrs;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        Path pathOnHost = pathOnHost(path);
        provider(pathOnHost).setAttribute(pathOnHost, attribute, fixAttributeValue(attribute, value), options);
    }

    private Object fixAttributeValue(String attribute, Object value) {
        int index = attribute.indexOf(':');
        if (index > 0) {
            switch (attribute.substring(index + 1)) {
                case "owner": return cast(value, ContainerUserPrincipal.class).baseFsPrincipal();
                case "group": return cast(value, ContainerGroupPrincipal.class).baseFsPrincipal();
                case "uid": return userPrincipalLookupService.userIdOnHost(cast(value, Integer.class));
                case "gid": return userPrincipalLookupService.groupIdOnHost(cast(value, Integer.class));
            }
        } // else basic file attribute
        return value;
    }

    private void fixOwnerToContainerRoot(ContainerPath path) throws IOException {
        setAttribute(path, "unix:uid", 0);
        setAttribute(path, "unix:gid", 0);
    }

    private class ContainerDirectoryStream implements DirectoryStream<Path> {
        private final DirectoryStream<Path> hostDirectoryStream;

        private ContainerDirectoryStream(DirectoryStream<Path> hostDirectoryStream) {
            this.hostDirectoryStream = hostDirectoryStream;
        }

        @Override
        public Iterator<Path> iterator() {
            Iterator<Path> hostPathIterator = hostDirectoryStream.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return hostPathIterator.hasNext();
                }

                @Override
                public Path next() {
                    Path pathOnHost = hostPathIterator.next();
                    return ContainerPath.fromPathOnHost(containerFs, pathOnHost);
                }
            };
        }

        @Override
        public void close() throws IOException {
            hostDirectoryStream.close();
        }
    }


    static ContainerPath toContainerPath(Path path) {
        return cast(path, ContainerPath.class);
    }

    private static <T> T cast(Object value, Class<T> type) {
        if (type.isInstance(value)) return type.cast(value);
        throw new ProviderMismatchException("Expected " + type.getSimpleName() + ", was " + value.getClass().getName());
    }

    private static Path pathOnHost(Path path) {
        return toContainerPath(path).pathOnHost();
    }

    private static FileSystemProvider provider(Path path) {
        return path.getFileSystem().provider();
    }
}
