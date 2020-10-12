// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLock;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.MockLogRetriever;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.deploy.DeployTester;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.Collection;
import java.util.List;

class MaintainerTester {

    private final Curator curator;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    MaintainerTester(Clock clock, TemporaryFolder temporaryFolder) throws IOException {
        this.curator = new MockCurator();
        InMemoryProvisioner hostProvisioner = new InMemoryProvisioner(true, false, "host0", "host1", "host2", "host3", "host4");
        ProvisionerAdapter provisioner = new ProvisionerAdapter(hostProvisioner);
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .hostedVespa(true)
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        GlobalComponentRegistry componentRegistry = new TestComponentRegistry.Builder()
                .curator(curator)
                .clock(clock)
                .configServerConfig(configserverConfig)
                .provisioner(provisioner)
                .modelFactoryRegistry(new ModelFactoryRegistry(List.of(new DeployTester.CountingModelFactory(clock))))
                .build();
        tenantRepository = new TenantRepository(componentRegistry);
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withProvisioner(provisioner)
                .withOrchestrator(new OrchestratorMock())
                .withLogRetriever(new MockLogRetriever())
                .withClock(clock)
                .withConfigserverConfig(configserverConfig)
                .build();
    }

    void deployApp(File applicationPath, PrepareParams.Builder prepareParams) {
        applicationRepository.deploy(applicationPath, prepareParams.ignoreValidationErrors(true).build());
    }

    Curator curator() { return curator; }

    TenantRepository tenantRepository() { return tenantRepository; }

    ApplicationRepository applicationRepository() { return applicationRepository;}


    private static class ProvisionerAdapter implements Provisioner {

        private final HostProvisioner hostProvisioner;

        public ProvisionerAdapter(HostProvisioner hostProvisioner) {
            this.hostProvisioner = hostProvisioner;
        }

        @Override
        public List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
            return hostProvisioner.prepare(cluster, capacity, logger);
        }

        @Override
        public void activate(NestedTransaction transaction, ApplicationId application, Collection<HostSpec> hosts) {
            // noop
        }

        @Override
        public void activate(NestedTransaction transaction, Collection<HostSpec> hosts, ProvisionLock lock) {

        }

        @Override
        public void remove(NestedTransaction transaction, ApplicationId application) {
            // noop
        }

        @Override
        public void remove(NestedTransaction transaction, ProvisionLock lock) {

        }

        @Override
        public void restart(ApplicationId application, HostFilter filter) {
            // noop
        }

        @Override
        public ProvisionLock lock(ApplicationId application) {
            return null;
        }

    }

}
