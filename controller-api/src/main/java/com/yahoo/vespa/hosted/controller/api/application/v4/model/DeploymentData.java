// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Data pertaining to a deployment to be done on a config server.
 *
 * @author jonmv
 */
public class DeploymentData {

    private final ApplicationId instance;
    private final ZoneId zone;
    private final byte[] applicationPackage;
    private final Version platform;
    private final Set<ContainerEndpoint> containerEndpoints;
    private final Optional<EndpointCertificateMetadata> endpointCertificateMetadata;
    private final Optional<DockerImage> dockerImageRepo;
    private final Optional<AthenzDomain> athenzDomain;
    private final Quota quota;
    private final List<TenantSecretStore> tenantSecretStores;
    private final List<X509Certificate> operatorCertificates;
    private final boolean dryRun;

    // TODO: Remove when users have been updated to use constructor below
    public DeploymentData(ApplicationId instance, ZoneId zone, byte[] applicationPackage, Version platform,
                          Set<ContainerEndpoint> containerEndpoints,
                          Optional<EndpointCertificateMetadata> endpointCertificateMetadata,
                          Optional<DockerImage> dockerImageRepo,
                          Optional<AthenzDomain> athenzDomain,
                          Quota quota,
                          List<TenantSecretStore> tenantSecretStores,
                          List<X509Certificate> operatorCertificates) {
        this(instance, zone, applicationPackage, platform, containerEndpoints, endpointCertificateMetadata,
             dockerImageRepo, athenzDomain, quota, tenantSecretStores, operatorCertificates, false);
    }

    public DeploymentData(ApplicationId instance, ZoneId zone, byte[] applicationPackage, Version platform,
                          Set<ContainerEndpoint> containerEndpoints,
                          Optional<EndpointCertificateMetadata> endpointCertificateMetadata,
                          Optional<DockerImage> dockerImageRepo,
                          Optional<AthenzDomain> athenzDomain,
                          Quota quota,
                          List<TenantSecretStore> tenantSecretStores,
                          List<X509Certificate> operatorCertificates,
                          boolean dryRun) {
        this.instance = requireNonNull(instance);
        this.zone = requireNonNull(zone);
        this.applicationPackage = requireNonNull(applicationPackage);
        this.platform = requireNonNull(platform);
        this.containerEndpoints = requireNonNull(containerEndpoints);
        this.endpointCertificateMetadata = requireNonNull(endpointCertificateMetadata);
        this.dockerImageRepo = requireNonNull(dockerImageRepo);
        this.athenzDomain = athenzDomain;
        this.quota = quota;
        this.tenantSecretStores = tenantSecretStores;
        this.operatorCertificates = operatorCertificates;
        this.dryRun = dryRun;
    }

    public ApplicationId instance() {
        return instance;
    }

    public ZoneId zone() {
        return zone;
    }

    public byte[] applicationPackage() {
        return applicationPackage;
    }

    public Version platform() {
        return platform;
    }

    public Set<ContainerEndpoint> containerEndpoints() {
        return containerEndpoints;
    }

    public Optional<EndpointCertificateMetadata> endpointCertificateMetadata() {
        return endpointCertificateMetadata;
    }

    public Optional<DockerImage> dockerImageRepo() {
        return dockerImageRepo;
    }

    public Optional<AthenzDomain> athenzDomain() {
        return athenzDomain;
    }

    public Quota quota() {
        return quota;
    }

    public List<TenantSecretStore> tenantSecretStores() {
        return tenantSecretStores;
    }

    public List<X509Certificate> operatorCertificates() {
        return operatorCertificates;
    }

    public boolean isDryRun() { return dryRun; }

}
