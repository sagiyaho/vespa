// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.yahoo.config.provision.SystemName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.yolean.concurrent.Sleeper;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.Rule;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ConfigServerRestExecutorImplTest {

    @Rule
    public final WireMockRule wireMock = new WireMockRule(options().dynamicPort(), true);

    @Test
    public void proxy_with_retries() throws Exception {
        var connectionReuseStrategy = new CountingConnectionReuseStrategy(Set.of("127.0.0.1"));
        var proxy = new ConfigServerRestExecutorImpl(new ZoneRegistryMock(SystemName.cd), SSLContext.getDefault(),
                                                     Sleeper.NOOP, connectionReuseStrategy);

        URI url = url();
        String path = url.getPath();
        stubRequests(path);

        HttpRequest request = HttpRequest.createTestRequest(url.toString(), com.yahoo.jdisc.http.HttpRequest.Method.GET);
        ProxyRequest proxyRequest = ProxyRequest.tryOne(url, path, request);

        // Request is retried
        HttpResponse response = proxy.handle(proxyRequest);
        wireMock.verify(3, getRequestedFor(urlEqualTo(path)));
        assertEquals(200, response.getStatus());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.render(out);
        assertEquals("OK", out.toString());

        // No connections are reused as host is a VIP
        assertEquals(0, connectionReuseStrategy.reusedConnections.get(url.getHost()).intValue());
    }

    @Test
    public void proxy_without_connection_reuse() throws Exception {
        var connectionReuseStrategy = new CountingConnectionReuseStrategy(Set.of());
        var proxy = new ConfigServerRestExecutorImpl(new ZoneRegistryMock(SystemName.cd), SSLContext.getDefault(),
                                                     (duration) -> {}, connectionReuseStrategy);

        URI url = url();
        String path = url.getPath();
        stubRequests(path);

        HttpRequest request = HttpRequest.createTestRequest(url.toString(), com.yahoo.jdisc.http.HttpRequest.Method.GET);
        ProxyRequest proxyRequest = ProxyRequest.tryOne(url, path, request);

        // Connections are reused
        assertEquals(200, proxy.handle(proxyRequest).getStatus());
        assertEquals(3, connectionReuseStrategy.reusedConnections.get(url.getHost()).intValue());
    }

    private URI url() {
        return URI.create("http://127.0.0.1:" + wireMock.port() + "/");
    }

    private void stubRequests(String path) {
        String retryScenario = "Retry scenario";
        String retryRequest = "Retry request 1";
        String retryRequestAgain = "Retry request 2";

        wireMock.stubFor(get(urlEqualTo(path)).inScenario(retryScenario)
                                              .whenScenarioStateIs(Scenario.STARTED)
                                              .willSetStateTo(retryRequest)
                                              .willReturn(aResponse().withStatus(500)));

        wireMock.stubFor(get(urlEqualTo(path)).inScenario(retryScenario)
                                              .whenScenarioStateIs(retryRequest)
                                              .willSetStateTo(retryRequestAgain)
                                              .willReturn(aResponse().withStatus(500)));

        wireMock.stubFor(get(urlEqualTo(path)).inScenario(retryScenario)
                                              .whenScenarioStateIs(retryRequestAgain)
                                              .willReturn(aResponse().withBody("OK")));
    }

    private static class CountingConnectionReuseStrategy extends ConfigServerRestExecutorImpl.ConnectionReuseStrategy {

        private final Map<String, Integer> reusedConnections = new HashMap<>();

        public CountingConnectionReuseStrategy(Set<String> vips) {
            super(vips);
        }

        @Override
        public boolean keepAlive(org.apache.http.HttpResponse response, HttpContext context) {
            boolean keepAlive = super.keepAlive(response, context);
            String host = HttpCoreContext.adapt(context).getTargetHost().getHostName();
            reusedConnections.putIfAbsent(host, 0);
            if (keepAlive) reusedConnections.compute(host, (ignored, count) -> ++count);
            return keepAlive;
        }

    }

}
