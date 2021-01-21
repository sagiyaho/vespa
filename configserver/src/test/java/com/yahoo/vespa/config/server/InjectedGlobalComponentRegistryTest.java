// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.vespa.config.server.filedistribution.FileServer;
import com.yahoo.vespa.config.server.host.ConfigRequestHostLivenessTracker;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.rpc.RpcRequestHandlerProvider;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.rpc.security.NoopRpcAuthorizer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class InjectedGlobalComponentRegistryTest {

    private RpcServer rpcServer;
    private ConfigDefinitionRepo defRepo;
    private GlobalComponentRegistry globalComponentRegistry;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupRegistry() throws IOException {
        ConfigserverConfig configserverConfig = new ConfigserverConfig(
                new ConfigserverConfig.Builder()
                        .configServerDBDir(temporaryFolder.newFolder("serverdb").getAbsolutePath())
                        .configDefinitionsDir(temporaryFolder.newFolder("configdefinitions").getAbsolutePath()));
        HostRegistry hostRegistry = new HostRegistry();
        rpcServer = new RpcServer(configserverConfig, null, Metrics.createTestMetrics(),
                                  hostRegistry, new ConfigRequestHostLivenessTracker(),
                                  new FileServer(temporaryFolder.newFolder("filereferences")),
                                  new NoopRpcAuthorizer(), new RpcRequestHandlerProvider());
        defRepo = new StaticConfigDefinitionRepo();
        globalComponentRegistry = new InjectedGlobalComponentRegistry(rpcServer, defRepo);
    }

    @Test
    public void testThatAllComponentsAreSetup() {
        assertThat(globalComponentRegistry.getReloadListener().hashCode(), is(rpcServer.hashCode()));
        assertThat(globalComponentRegistry.getTenantListener().hashCode(), is(rpcServer.hashCode()));
        assertThat(globalComponentRegistry.getStaticConfigDefinitionRepo(), is(defRepo));
    }

}
