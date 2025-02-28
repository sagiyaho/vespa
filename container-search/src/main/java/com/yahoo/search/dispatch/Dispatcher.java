// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.compress.Compressor;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.jdisc.Metric;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.SearchPath.InvalidSearchPathException;
import com.yahoo.search.dispatch.rpc.RpcInvokerFactory;
import com.yahoo.search.dispatch.rpc.RpcPingFactory;
import com.yahoo.search.dispatch.rpc.RpcResourcePool;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A dispatcher communicates with search nodes to perform queries and fill hits.
 *
 * This class allocates {@link SearchInvoker} and {@link FillInvoker} objects based
 * on query properties and general system status. The caller can then use the provided
 * invocation object to execute the search or fill.
 *
 * This class is multithread safe.
 *
 * @author bratseth
 * @author ollvir
 */
public class Dispatcher extends AbstractComponent {

    public static final String DISPATCH = "dispatch";
    private static final String INTERNAL = "internal";
    private static final String PROTOBUF = "protobuf";
    private static final String TOP_K_PROBABILITY = "topKProbability";

    private static final String INTERNAL_METRIC = "dispatch_internal";

    private static final int MAX_GROUP_SELECTION_ATTEMPTS = 3;

    /** If enabled, search queries will use protobuf rpc */
    public static final CompoundName dispatchProtobuf = CompoundName.fromComponents(DISPATCH, PROTOBUF);

    /** If set will control computation of how many hits will be fetched from each partition.*/
    public static final CompoundName topKProbability = CompoundName.fromComponents(DISPATCH, TOP_K_PROBABILITY);

    /** A model of the search cluster this dispatches to */
    private final SearchCluster searchCluster;
    private final ClusterMonitor<Node> clusterMonitor;

    private final LoadBalancer loadBalancer;

    private final InvokerFactory invokerFactory;

    private final Metric metric;
    private final Metric.Context metricContext;

    private final int maxHitsPerNode;

    private static final QueryProfileType argumentType;

    static {
        argumentType = new QueryProfileType(DISPATCH);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(INTERNAL, FieldType.booleanType));
        argumentType.addField(new FieldDescription(PROTOBUF, FieldType.booleanType));
        argumentType.addField(new FieldDescription(TOP_K_PROBABILITY, FieldType.doubleType));
        argumentType.freeze();
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    @Inject
    public Dispatcher(RpcResourcePool resourcePool,
                      ComponentId clusterId,
                      DispatchConfig dispatchConfig,
                      VipStatus vipStatus,
                      Metric metric) {
        this(resourcePool, new SearchCluster(clusterId.stringValue(), dispatchConfig,
                                             vipStatus, new RpcPingFactory(resourcePool)),
             dispatchConfig, metric);

    }

    private Dispatcher(RpcResourcePool resourcePool, SearchCluster searchCluster, DispatchConfig dispatchConfig, Metric metric) {
        this(new ClusterMonitor<>(searchCluster, true), searchCluster, dispatchConfig, new RpcInvokerFactory(resourcePool, searchCluster), metric);
    }

    /* Protected for simple mocking in tests. Beware that searchCluster is shutdown on in deconstruct() */
    protected Dispatcher(ClusterMonitor<Node> clusterMonitor,
                         SearchCluster searchCluster,
                         DispatchConfig dispatchConfig,
                         InvokerFactory invokerFactory,
                         Metric metric) {
        if (dispatchConfig.useMultilevelDispatch())
            throw new IllegalArgumentException(searchCluster + " is configured with multilevel dispatch, but this is not supported");

        this.searchCluster = searchCluster;
        this.clusterMonitor = clusterMonitor;
        this.loadBalancer = new LoadBalancer(searchCluster,
                                  dispatchConfig.distributionPolicy() == DispatchConfig.DistributionPolicy.ROUNDROBIN);
        this.invokerFactory = invokerFactory;
        this.metric = metric;
        this.metricContext = metric.createContext(null);
        this.maxHitsPerNode = dispatchConfig.maxHitsPerNode();
        searchCluster.addMonitoring(clusterMonitor);
        Thread warmup = new Thread(() -> warmup(dispatchConfig.warmuptime()));
        warmup.start();
        try {
            while ( ! searchCluster.hasInformationAboutAllNodes()) {
                Thread.sleep(1);
            }
            warmup.join();
        } catch (InterruptedException e) {}

        // Now we have information from all nodes and a ping iteration has completed.
        // Instead of waiting until next ping interval to update coverage and group state,
        // we should compute the state ourselves, so that when the dispatcher is ready the state
        // of its groups are also known.
        searchCluster.pingIterationCompleted();
    }

    /**
     * Will run important code in order to trigger JIT compilation and avoid cold start issues.
     * Currently warms up lz4 compression code.
     */
    private static long warmup(double seconds) {
        return new Compressor().warmup(seconds);
    }

    /** Returns the search cluster this dispatches to */
    public SearchCluster searchCluster() {
        return searchCluster;
    }

    @Override
    public void deconstruct() {
        // The clustermonitor must be shutdown first as it uses the invokerfactory through the searchCluster.
        clusterMonitor.shutdown();
        invokerFactory.release();
    }

    public FillInvoker getFillInvoker(Result result, VespaBackEndSearcher searcher) {
        return invokerFactory.createFillInvoker(searcher, result);
    }

    public SearchInvoker getSearchInvoker(Query query, VespaBackEndSearcher searcher) {
        SearchInvoker invoker = getSearchPathInvoker(query, searcher).orElseGet(() -> getInternalInvoker(query, searcher));

        if (query.properties().getBoolean(com.yahoo.search.query.Model.ESTIMATE)) {
            query.setHits(0);
            query.setOffset(0);
        }
        metric.add(INTERNAL_METRIC, 1, metricContext);
        return invoker;
    }

    /** Builds an invoker based on searchpath */
    private Optional<SearchInvoker> getSearchPathInvoker(Query query, VespaBackEndSearcher searcher) {
        String searchPath = query.getModel().getSearchPath();
        if (searchPath == null) return Optional.empty();

        try {
            List<Node> nodes = SearchPath.selectNodes(searchPath, searchCluster);
            if (nodes.isEmpty()) return Optional.empty();

            query.trace(false, 2, "Dispatching with search path ", searchPath);
            return invokerFactory.createSearchInvoker(searcher,
                                                      query,
                                                      nodes,
                                                      true,
                                                      maxHitsPerNode);
        } catch (InvalidSearchPathException e) {
            return Optional.of(new SearchErrorInvoker(ErrorMessage.createIllegalQuery(e.getMessage())));
        }
    }

    private SearchInvoker getInternalInvoker(Query query, VespaBackEndSearcher searcher) {
        Optional<Node> directNode = searchCluster.localCorpusDispatchTarget();
        if (directNode.isPresent()) {
            Node node = directNode.get();
            query.trace(false, 2, "Dispatching to ", node);
            return invokerFactory.createSearchInvoker(searcher,
                                                      query,
                                                      List.of(node),
                                                      true,
                                                      maxHitsPerNode)
                                 .orElseThrow(() -> new IllegalStateException("Could not dispatch directly to " + node));
        }

        int covered = searchCluster.groupsWithSufficientCoverage();
        int groups = searchCluster.orderedGroups().size();
        int max = Integer.min(Integer.min(covered + 1, groups), MAX_GROUP_SELECTION_ATTEMPTS);
        Set<Integer> rejected = rejectGroupBlockingFeed(searchCluster.orderedGroups());
        for (int i = 0; i < max; i++) {
            Optional<Group> groupInCluster = loadBalancer.takeGroup(rejected);
            if (groupInCluster.isEmpty()) break; // No groups available

            Group group = groupInCluster.get();
            boolean acceptIncompleteCoverage = (i == max - 1);
            Optional<SearchInvoker> invoker = invokerFactory.createSearchInvoker(searcher,
                                                                                 query,
                                                                                 group.nodes(),
                                                                                 acceptIncompleteCoverage,
                                                                                 maxHitsPerNode);
            if (invoker.isPresent()) {
                query.trace(false, 2, "Dispatching to group ", group.id());
                query.getModel().setSearchPath("/" + group.id());
                invoker.get().teardown((success, time) -> loadBalancer.releaseGroup(group, success, time));
                return invoker.get();
            } else {
                loadBalancer.releaseGroup(group, false, 0);
                if (rejected == null) {
                    rejected = new HashSet<>();
                }
                rejected.add(group.id());
            }
        }
        throw new IllegalStateException("No suitable groups to dispatch query. Rejected: " + rejected);
    }

    /**
     * We want to avoid groups blocking feed because their data may be out of date.
     * If there is a single group blocking feed, we want to reject it.
     * If multiple groups are blocking feed we should use them anyway as we may not have remaining
     * capacity otherwise. Same if there are no other groups.
     *
     * @return a modifiable set containing the single group to reject, or null otherwise
     */
    private Set<Integer> rejectGroupBlockingFeed(List<Group> groups) {
        if (groups.size() == 1) return null;
        List<Group> groupsRejectingFeed = groups.stream().filter(Group::isBlockingWrites).collect(Collectors.toList());
        if (groupsRejectingFeed.size() != 1) return null;
        Set<Integer> rejected = new HashSet<>();
        rejected.add(groupsRejectingFeed.get(0).id());
        return rejected;
    }

}
