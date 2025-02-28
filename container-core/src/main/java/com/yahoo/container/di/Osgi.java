// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.FileReference;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.bundle.MockBundle;
import org.osgi.framework.Bundle;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 * @author ollivir
 */
public interface Osgi {

    default void installPlatformBundles(Collection<String> bundlePaths) {
        System.out.println("installPlatformBundles " + bundlePaths);
    }

    /**
     * Returns the set of bundles that is not used by the current application generation,
     * and therefore should be scheduled for uninstalling.
     */
    default Set<Bundle> useApplicationBundles(Collection<FileReference> bundles) {
        System.out.println("useBundles " + bundles.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return emptySet();
    }

    default Class<?> resolveClass(BundleInstantiationSpecification spec) {
        System.out.println("resolving class " + spec.classId);
        try {
            return Class.forName(spec.classId.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    default Bundle getBundle(ComponentSpecification spec) {
        System.out.println("resolving bundle " + spec);
        return new MockBundle();
    }
}
