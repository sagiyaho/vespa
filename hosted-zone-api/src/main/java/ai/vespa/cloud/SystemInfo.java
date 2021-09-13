// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

/**
 * Provides information about the system in which this container is running.
 * This is available and can be injected when running in a cloud environment.
 *
 * @author bratseth
 */
public class SystemInfo {

    private final Zone zone;
    private final Cluster cluster;
    private final Node node;

    public SystemInfo(Zone zone, Cluster cluster, Node node) {
        this.zone = zone;
        this.cluster = cluster;
        this.node = node;
    }

    /** Returns the zone this is running in */
    public Zone zone() { return zone; }

    /** Returns the cluster this is part of */
    public Cluster cluster() { return cluster; }

    /** Returns the node this is running on */
    public Node node() { return node; }

}
