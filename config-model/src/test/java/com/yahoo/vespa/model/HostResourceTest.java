// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static com.yahoo.config.provision.ClusterSpec.Type.container;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 */
public class HostResourceTest {

    @Test
    public void require_exception_when_no_matching_hostalias() {
        MockRoot root = new MockRoot();
        TestService service = new TestService(root, 1);

        try {
            service.initService(root.deployLogger());
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), endsWith("No host found for service 'hostresourcetest$testservice0'. " +
                    "The hostalias is probably missing from hosts.xml."));
        }
    }

    @Test
    public void host_witrh_membership() {
        HostResource host = hostResourceWithMemberships(ClusterMembership.from(clusterSpec(container, "jdisc"), 0));
        assertClusterMembership(host, container, "jdisc");
    }

    private void assertClusterMembership(HostResource host, ClusterSpec.Type type, String id) {
        ClusterSpec membership = host.spec().membership().map(ClusterMembership::cluster)
                .orElseThrow(() -> new RuntimeException("No cluster membership!"));

        assertEquals(type, membership.type());
        assertEquals(id, membership.id().value());
    }

    private static ClusterSpec clusterSpec(ClusterSpec.Type type, String id) {
        return ClusterSpec.specification(type, ClusterSpec.Id.from(id)).group(ClusterSpec.Group.from(0)).vespaVersion("6.42").build();
    }

    private static HostResource hostResourceWithMemberships(ClusterMembership membership) {
        return new HostResource(Host.createHost(null, "hostname"),
                                new HostSpec("hostname",
                                             NodeResources.unspecified(), NodeResources.unspecified(), NodeResources.unspecified(),
                                             membership,
                                             Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private static int counter = 0;
    int getCounter() { return ++counter; }

    private class TestService extends AbstractService {
        private final int portCount;

        TestService(AbstractConfigProducer parent, int portCount) {
            super(parent, "testService" + getCounter());
            this.portCount = portCount;
        }

        @Override
        public boolean requiresWantedPort() {
            return true;
        }

        @Override
        public int getPortCount() { return portCount; }

        @Override
        public void allocatePorts(int start, PortAllocBridge from) {
            for (int i = 0; i < portCount; i++) {
                String suffix = "generic." + i;
                if (start == 0) {
                    from.allocatePort(suffix);
                } else {
                    from.requirePort(start++, suffix);
                }
            }
        }

    }
}
