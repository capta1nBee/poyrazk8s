package com.k8s.platform.service.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.network.NetworkFlow;
import com.k8s.platform.domain.entity.network.NetworkTopologyEdge;
import com.k8s.platform.domain.entity.k8s.Pod;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.PodRepository;
import com.k8s.platform.domain.repository.k8s.ServiceRepository;
import com.k8s.platform.domain.repository.network.NetworkFlowRepository;
import com.k8s.platform.domain.repository.network.NetworkTopologyEdgeRepository;
import com.k8s.platform.dto.network.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkFlowService {

    private final NetworkFlowRepository flowRepository;
    private final NetworkTopologyEdgeRepository topologyRepository;
    private final PodRepository podRepository;
    private final ServiceRepository serviceRepository;
    private final ClusterRepository clusterRepository;
    private final ObjectMapper objectMapper;
    /** Used to direct inserts to the currently-active rotation table. */
    private final JdbcTemplate jdbcTemplate;
    private final NetworkFlowTableRouter tableRouter;

    // ── INSERT SQL (42 bind params, table name substituted at runtime) ──────
    private static final String INSERT_COLS =
            "flow_id, cluster_uid, flow_type, timestamp, " +
            "source_pod_name, source_namespace, source_kind, source_ip, source_port, " +
            "source_node_name, source_ingress, source_egress, source_drop, source_pass, " +
            "destination_pod_name, destination_namespace, destination_kind, destination_ip, destination_port, " +
            "destination_node_name, destination_ingress, destination_egress, destination_drop, destination_pass, " +
            "service_name, service_namespace, backend_pod_name, backend_pod_namespace, " +
            "protocol, tcp_flags, bytes, interface_name, direction, " +
            "l7_protocol, l7_method, l7_host, l7_url, l7_path, l7_status_code, l7_content_type, " +
            "node_name, created_at";
    private static final String INSERT_PLACEHOLDERS =
            "?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?, ?,?,?,?,?, ?,?,?,?,?,?,?, ?,?";

    /** Returns the 42 bind-parameter values for a single NetworkFlow row. */
    private Object[] flowParams(NetworkFlow f) {
        return new Object[] {
            f.getFlowId(), f.getClusterUid(), f.getFlowType(), f.getTimestamp(),
            f.getSourcePodName(), f.getSourceNamespace(), f.getSourceKind(), f.getSourceIp(), f.getSourcePort(),
            f.getSourceNodeName(), f.getSourceIngress(), f.getSourceEgress(), f.getSourceDrop(), f.getSourcePass(),
            f.getDestinationPodName(), f.getDestinationNamespace(), f.getDestinationKind(),
            f.getDestinationIp(), f.getDestinationPort(),
            f.getDestinationNodeName(), f.getDestinationIngress(), f.getDestinationEgress(),
            f.getDestinationDrop(), f.getDestinationPass(),
            f.getServiceName(), f.getServiceNamespace(), f.getBackendPodName(), f.getBackendPodNamespace(),
            f.getProtocol(), f.getTcpFlags(), f.getBytes(), f.getInterfaceName(), f.getDirection(),
            f.getL7Protocol(), f.getL7Method(), f.getL7Host(), f.getL7Url(), f.getL7Path(),
            f.getL7StatusCode(), f.getL7ContentType(),
            f.getNodeName(), f.getCreatedAt() != null ? f.getCreatedAt() : LocalDateTime.now()
        };
    }

    /**
     * Save a single network flow event directly into the active rotation table.
     */
    public NetworkFlow saveFlow(NetworkFlowDTO dto) {
        log.info("Saving network flow: {} {} -> {}",
                dto.getFlowType(),
                dto.getSource() != null ? dto.getSource().getPodName() : "unknown",
                dto.getDestination() != null ? dto.getDestination().getPodName() : "unknown");

        NetworkFlow flow = convertToEntity(dto);
        flow.setCreatedAt(LocalDateTime.now());
        String table = tableRouter.getActiveTable();
        jdbcTemplate.update(
                "INSERT INTO " + table + " (" + INSERT_COLS + ") VALUES (" + INSERT_PLACEHOLDERS + ")",
                flowParams(flow));

        // Update topology edge
        updateTopologyEdge(dto);

        return flow; // id not populated (not required by callers)
    }

    /**
     * Save multiple flow events via JdbcTemplate batch INSERT into the active
     * rotation table.  Bypasses JPA so all rows land in the correct physical
     * table (network_flows_1 or network_flows_2) instead of the UNION-ALL view.
     */
    public int saveFlowBatch(List<NetworkFlowDTO> dtos, String clusterUid, String nodeName) {
        log.info("========================================");
        log.info("SAVING FLOW BATCH TO DATABASE");
        log.info("  Cluster UID: {}", clusterUid);
        log.info("  Node Name:   {}", nodeName);
        log.info("  Batch Size:  {}", dtos.size());
        log.info("========================================");

        try {
            List<NetworkFlow> flows = dtos.stream()
                    .map(dto -> {
                        dto.setClusterUid(clusterUid);
                        dto.setNodeName(nodeName);
                        NetworkFlow f = convertToEntity(dto);
                        f.setCreatedAt(LocalDateTime.now());
                        return f;
                    })
                    .collect(Collectors.toList());

            String table = tableRouter.getActiveTable();
            String sql = "INSERT INTO " + table + " (" + INSERT_COLS + ") VALUES (" + INSERT_PLACEHOLDERS + ")";

            List<Object[]> params = flows.stream()
                    .map(this::flowParams)
                    .collect(Collectors.toList());
            jdbcTemplate.batchUpdate(sql, params);

            log.info("Successfully persisted {} flows to {}", flows.size(), table);

            // Update topology edges in aggregate
            Map<String, TopologyAggregation> aggregatedEdges = new HashMap<>();
            for (NetworkFlowDTO dto : dtos) {
                if (dto.getFlowType() == null || "unknown".equalsIgnoreCase(dto.getFlowType())) {
                    continue;
                }
                String key = getTopologyKey(dto);
                aggregatedEdges.computeIfAbsent(key, k -> new TopologyAggregation(dto)).addFlow(dto);
            }

            int edgesUpdated = 0;
            for (TopologyAggregation agg : aggregatedEdges.values()) {
                try {
                    updateTopologyEdgeAggregated(agg);
                    edgesUpdated++;
                } catch (Exception e) {
                    log.warn("Failed to update topology edge for aggregate: {}", e.getMessage());
                }
            }
            log.info("Updated {} unique topology edges from {} flows", edgesUpdated, dtos.size());

            return flows.size();
        } catch (Exception e) {
            log.error("Failed to save flow batch: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Query flows with filters
     */
    public Page<NetworkFlowDTO> queryFlows(NetworkFlowFilterDTO filter) {
        Pageable pageable = createPageable(filter);

        Specification<NetworkFlow> spec = createSpecification(filter);
        Page<NetworkFlow> flows = flowRepository.findAll(spec, pageable);

        return flows.map(this::convertToDTO);
    }

    /**
     * Get flow statistics
     */
    public NetworkFlowStatsDTO getFlowStats(String clusterUid, LocalDateTime startTime, LocalDateTime endTime) {
        long totalFlows = flowRepository.countByClusterUidAndTimestampBetween(clusterUid, startTime, endTime);

        List<Object[]> byType = flowRepository.getFlowStatsByType(clusterUid, startTime, endTime);
        List<Object[]> byProtocol = flowRepository.getFlowStatsByProtocol(clusterUid, startTime, endTime);
        List<Object[]> bySrcNs = flowRepository.getFlowStatsBySourceNamespace(clusterUid, startTime, endTime);
        List<Object[]> byDstNs = flowRepository.getFlowStatsByDestinationNamespace(clusterUid, startTime, endTime);

        long totalBytes = byType.stream()
                .mapToLong(row -> row[2] != null ? ((Number) row[2]).longValue() : 0L)
                .sum();

        return NetworkFlowStatsDTO.builder()
                .totalFlows(totalFlows)
                .totalBytes(totalBytes)
                .byFlowType(convertToStatItems(byType, totalFlows))
                .byProtocol(convertToStatItems(byProtocol, totalFlows))
                .bySourceNamespace(convertToStatItems(bySrcNs, totalFlows))
                .byDestinationNamespace(convertToStatItems(byDstNs, totalFlows))
                .build();
    }

    /**
     * Get network topology
     */
    public NetworkTopologyDTO getTopology(String clusterUid, List<String> namespaces) {
        List<NetworkTopologyEdge> edges;

        if (namespaces != null && !namespaces.isEmpty()) {
            edges = topologyRepository.findByClusterUidAndNamespaces(clusterUid, namespaces);
        } else {
            edges = topologyRepository.findByClusterUid(clusterUid);
        }

        // If no edges in topology table, generate from flows directly
        if (edges.isEmpty()) {
            log.info("No topology edges found, generating from flows for cluster: {}", clusterUid);
            return generateTopologyFromFlows(clusterUid, namespaces);
        }

        // Pre-fetch pod labels
        Map<String, Map<String, String>> podLabelMap = new HashMap<>();
        Long clusterId = clusterRepository.findByUid(clusterUid)
                .map(c -> c.getId())
                .orElse(null);

        if (clusterId != null) {
            Set<String> backendPodKeys = new HashSet<>();
            for (NetworkTopologyEdge edge : edges) {
                if (edge.getBackendPodName() != null && edge.getBackendPodNamespace() != null) {
                    backendPodKeys.add(edge.getBackendPodNamespace() + ":" + edge.getBackendPodName());
                }
            }

            List<Pod> clusterPods;
            if (namespaces != null && !namespaces.isEmpty()) {
                clusterPods = new ArrayList<>();
                for (String ns : namespaces) {
                    clusterPods.addAll(podRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(clusterId, ns));
                }

                // Fetch explicitly mentioned backend pods if not already covered
                for (String key : backendPodKeys) {
                    String[] parts = key.split(":");
                    if (!namespaces.contains(parts[0])) {
                        podRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, parts[0], parts[1])
                                .ifPresent(clusterPods::add);
                    }
                }
            } else {
                clusterPods = podRepository.findByClusterIdAndIsDeletedFalse(clusterId);
            }

            for (Pod p : clusterPods) {
                if (p.getLabels() != null && !p.getLabels().isBlank()) {
                    try {
                        Map<String, String> labels = objectMapper.readValue(p.getLabels(),
                                new TypeReference<Map<String, String>>() {
                                });
                        podLabelMap.put(p.getNamespace() + ":" + p.getName(), labels);
                    } catch (Exception e) {
                        log.warn("Failed to parse labels for pod {}", p.getName());
                    }
                }
            }
        }

        // Build nodes from edges
        Set<String> nodeIds = new HashSet<>();
        List<NetworkTopologyDTO.TopologyNode> nodes = new ArrayList<>();

        for (NetworkTopologyEdge edge : edges) {
            // Source node
            String srcId = buildNodeId(edge.getSourceType(), edge.getSourceNamespace(), edge.getSourceName());
            if (!nodeIds.contains(srcId)) {
                nodeIds.add(srcId);
                nodes.add(NetworkTopologyDTO.TopologyNode.builder()
                        .id(srcId)
                        .name(edge.getSourceName())
                        .namespace(edge.getSourceNamespace())
                        .type(edge.getSourceType())
                        .label(edge.getSourceName())
                        .group(edge.getSourceNamespace() != null ? edge.getSourceNamespace() : "external")
                        .connectionCount(0)
                        .totalBytes(0L)
                        .podLabels("pod".equals(edge.getSourceType())
                                ? podLabelMap.get(edge.getSourceNamespace() + ":" + edge.getSourceName())
                                : null)
                        .build());
            }

            // Target node
            String tgtId = buildNodeId(edge.getTargetType(), edge.getTargetNamespace(), edge.getTargetName());
            if (!nodeIds.contains(tgtId)) {
                nodeIds.add(tgtId);
                nodes.add(NetworkTopologyDTO.TopologyNode.builder()
                        .id(tgtId)
                        .name(edge.getTargetName())
                        .namespace(edge.getTargetNamespace())
                        .type(edge.getTargetType())
                        .label(edge.getTargetName())
                        .group(edge.getTargetNamespace() != null ? edge.getTargetNamespace() : "external")
                        .connectionCount(0)
                        .totalBytes(0L)
                        .podLabels("pod".equals(edge.getTargetType())
                                ? podLabelMap.get(edge.getTargetNamespace() + ":" + edge.getTargetName())
                                : null)
                        .build());
            }
        }

        // Calculate max bytes for edge weight normalization
        long maxBytes = edges.stream()
                .mapToLong(e -> e.getTotalBytes() != null ? e.getTotalBytes() : 0L)
                .max()
                .orElse(1L);

        // Build edges
        List<NetworkTopologyDTO.TopologyEdge> topologyEdges = edges.stream()
                .map(edge -> {
                    String srcId = buildNodeId(edge.getSourceType(), edge.getSourceNamespace(), edge.getSourceName());
                    String tgtId = buildNodeId(edge.getTargetType(), edge.getTargetNamespace(), edge.getTargetName());

                    long bytes = edge.getTotalBytes() != null ? edge.getTotalBytes() : 0L;
                    double weight = maxBytes > 0 ? (double) bytes / maxBytes : 0.5;

                    return NetworkTopologyDTO.TopologyEdge.builder()
                            .id(edge.getId())
                            .source(srcId)
                            .target(tgtId)
                            .protocol(edge.getProtocol())
                            .port(edge.getPort())
                            .flowCount(edge.getFlowCount())
                            .totalBytes(bytes)
                            .lastSeen(edge.getLastSeen())
                            .label(edge.getProtocol() + ":" + edge.getPort())
                            .weight(Math.max(0.1, weight))
                            .build();
                })
                .collect(Collectors.toList());

        // Update node connection counts
        Map<String, Integer> connectionCounts = new HashMap<>();
        Map<String, Long> nodeBytes = new HashMap<>();
        for (NetworkTopologyDTO.TopologyEdge edge : topologyEdges) {
            connectionCounts.merge(edge.getSource(), 1, Integer::sum);
            connectionCounts.merge(edge.getTarget(), 1, Integer::sum);
            nodeBytes.merge(edge.getSource(), edge.getTotalBytes(), Long::sum);
            nodeBytes.merge(edge.getTarget(), edge.getTotalBytes(), Long::sum);
        }

        nodes.forEach(node -> {
            node.setConnectionCount(connectionCounts.getOrDefault(node.getId(), 0));
            node.setTotalBytes(nodeBytes.getOrDefault(node.getId(), 0L));
        });

        // Add observed backend pods to nodes if they are missing
        long logicalEdgeIdCounter = -5000L;
        for (NetworkTopologyEdge edge : edges) {
            if ("service".equals(edge.getTargetType()) && edge.getBackendPodName() != null) {
                String svcId = buildNodeId(edge.getTargetType(), edge.getTargetNamespace(), edge.getTargetName());
                String podId = buildNodeId("pod", edge.getBackendPodNamespace(), edge.getBackendPodName());

                if (!nodeIds.contains(podId)) {
                    nodeIds.add(podId);
                    nodes.add(NetworkTopologyDTO.TopologyNode.builder()
                            .id(podId)
                            .name(edge.getBackendPodName())
                            .namespace(edge.getBackendPodNamespace())
                            .type("pod")
                            .label(edge.getBackendPodName())
                            .group(edge.getBackendPodNamespace())
                            .connectionCount(0)
                            .totalBytes(0L)
                            .podLabels(podLabelMap.get(edge.getBackendPodNamespace() + ":" + edge.getBackendPodName()))
                            .build());
                }

                // Add logical edge from Service to Backend
                topologyEdges.add(NetworkTopologyDTO.TopologyEdge.builder()
                        .id(logicalEdgeIdCounter--)
                        .source(svcId)
                        .target(podId)
                        .protocol(edge.getProtocol())
                        .port(edge.getPort())
                        .flowCount(edge.getFlowCount())
                        .totalBytes(edge.getTotalBytes())
                        .lastSeen(edge.getLastSeen())
                        .label("backend")
                        .weight(0.1)
                        .isLogical(true)
                        .build());
            }
        }

        injectLogicalServiceEdges(nodes, topologyEdges, nodeIds, podLabelMap, namespaces, clusterId);

        return NetworkTopologyDTO.builder()
                .nodes(nodes)
                .edges(topologyEdges)
                .build();
    }

    /**
     * Get filter options
     */
    public Map<String, List<String>> getFilterOptions(String clusterUid) {
        Map<String, List<String>> options = new HashMap<>();
        options.put("flowTypes", flowRepository.findDistinctFlowTypes(clusterUid));
        options.put("sourceNamespaces", flowRepository.findDistinctSourceNamespaces(clusterUid));
        options.put("destinationNamespaces", flowRepository.findDistinctDestinationNamespaces(clusterUid));
        options.put("protocols", flowRepository.findDistinctProtocols(clusterUid));
        options.put("sourcePods", flowRepository.findDistinctSourcePods(clusterUid));
        options.put("destinationPods", flowRepository.findDistinctDestinationPods(clusterUid));
        return options;
    }

    /**
     * Generate topology directly from flows (fallback when edges table is empty)
     */
    private NetworkTopologyDTO generateTopologyFromFlows(String clusterUid, List<String> namespaces) {
        // Get flows for this cluster
        NetworkFlowFilterDTO filter = NetworkFlowFilterDTO.builder()
                .clusterUid(clusterUid)
                .page(0)
                .pageSize(1000) // Limit for performance
                .sortBy("timestamp")
                .sortDesc(true)
                .build();

        if (namespaces != null && !namespaces.isEmpty()) {
            filter.setSourceNamespaces(namespaces);
        }

        Page<NetworkFlowDTO> flowsPage = queryFlows(filter);
        List<NetworkFlowDTO> flowList = flowsPage.getContent();

        // Pre-fetch pod labels
        Map<String, Map<String, String>> podLabelMap = new HashMap<>();
        Long clusterId = clusterRepository.findByUid(clusterUid)
                .map(c -> c.getId())
                .orElse(null);

        if (clusterId != null) {
            Set<String> backendPodKeys = new HashSet<>();
            for (NetworkFlowDTO flow : flowList) {
                if (flow.getDestination() != null && flow.getDestination().getService() != null) {
                    String bpName = flow.getDestination().getService().getBackendPodName();
                    String bpNs = flow.getDestination().getService().getBackendPodNamespace();
                    if (bpName != null && bpNs != null) {
                        backendPodKeys.add(bpNs + ":" + bpName);
                    }
                }
            }

            List<Pod> clusterPods;
            if (namespaces != null && !namespaces.isEmpty()) {
                clusterPods = new ArrayList<>();
                for (String ns : namespaces) {
                    clusterPods.addAll(podRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(clusterId, ns));
                }

                // Fetch explicitly mentioned backend pods if not already covered
                for (String key : backendPodKeys) {
                    String[] parts = key.split(":");
                    if (!namespaces.contains(parts[0])) {
                        podRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, parts[0], parts[1])
                                .ifPresent(clusterPods::add);
                    }
                }
            } else {
                clusterPods = podRepository.findByClusterIdAndIsDeletedFalse(clusterId);
            }

            for (Pod p : clusterPods) {
                if (p.getLabels() != null && !p.getLabels().isBlank()) {
                    try {
                        Map<String, String> labels = objectMapper.readValue(p.getLabels(),
                                new TypeReference<Map<String, String>>() {
                                });
                        podLabelMap.put(p.getNamespace() + ":" + p.getName(), labels);
                    } catch (Exception e) {
                        log.warn("Failed to parse labels for pod {}", p.getName());
                    }
                }
            }
        }

        // Build topology from flows
        Set<String> nodeIds = new HashSet<>();
        List<NetworkTopologyDTO.TopologyNode> nodes = new ArrayList<>();
        Map<String, NetworkTopologyDTO.TopologyEdge> edgeMap = new HashMap<>();

        for (NetworkFlowDTO flow : flowList) {
            if (flow.getSource() == null || flow.getDestination() == null)
                continue;

            // Source node
            String srcType = flow.getSource().getKind() != null ? flow.getSource().getKind().toLowerCase() : "pod";
            String srcName = flow.getSource().getPodName() != null ? flow.getSource().getPodName()
                    : flow.getSource().getIp();
            String srcNs = flow.getSource().getNamespace();
            String srcId = buildNodeId(srcType, srcNs, srcName);

            if (!nodeIds.contains(srcId)) {
                nodeIds.add(srcId);
                nodes.add(NetworkTopologyDTO.TopologyNode.builder()
                        .id(srcId)
                        .name(srcName)
                        .namespace(srcNs)
                        .type(srcType)
                        .label(srcName)
                        .group(srcNs != null ? srcNs : "external")
                        .connectionCount(0)
                        .totalBytes(0L)
                        .podLabels("pod".equals(srcType) ? podLabelMap.get(srcNs + ":" + srcName) : null)
                        .build());
            }

            // Destination node
            String dstType = flow.getDestination().getKind() != null ? flow.getDestination().getKind().toLowerCase()
                    : "external";
            String dstName = flow.getDestination().getPodName() != null ? flow.getDestination().getPodName()
                    : flow.getDestination().getIp();
            String dstNs = flow.getDestination().getNamespace();
            String dstId = buildNodeId(dstType, dstNs, dstName);

            if (!nodeIds.contains(dstId)) {
                nodeIds.add(dstId);
                nodes.add(NetworkTopologyDTO.TopologyNode.builder()
                        .id(dstId)
                        .name(dstName)
                        .namespace(dstNs)
                        .type(dstType)
                        .label(dstName)
                        .group(dstNs != null ? dstNs : "external")
                        .connectionCount(0)
                        .totalBytes(0L)
                        .podLabels("pod".equals(dstType) ? podLabelMap.get(dstNs + ":" + dstName) : null)
                        .build());
            }

            // Edge
            String protocol = flow.getNetwork() != null ? flow.getNetwork().getProtocol() : "TCP";
            Integer port = flow.getDestination().getPort();
            String edgeKey = srcId + "->" + dstId + ":" + protocol + ":" + port;

            long bytes = flow.getNetwork() != null && flow.getNetwork().getBytes() != null
                    ? flow.getNetwork().getBytes()
                    : 0L;

            if (edgeMap.containsKey(edgeKey)) {
                NetworkTopologyDTO.TopologyEdge existing = edgeMap.get(edgeKey);
                existing.setFlowCount(existing.getFlowCount() + 1);
                existing.setTotalBytes(existing.getTotalBytes() + bytes);
            } else {
                edgeMap.put(edgeKey, NetworkTopologyDTO.TopologyEdge.builder()
                        .id((long) edgeKey.hashCode())
                        .source(srcId)
                        .target(dstId)
                        .protocol(protocol)
                        .port(port)
                        .flowCount(1L)
                        .totalBytes(bytes)
                        .lastSeen(flow.getTimestamp())
                        .label(protocol + ":" + port)
                        .weight(0.5)
                        .build());
            }
        }

        List<NetworkTopologyDTO.TopologyEdge> topologyEdges = new ArrayList<>(edgeMap.values());

        // Calculate max bytes for normalization
        long maxBytes = topologyEdges.stream()
                .mapToLong(e -> e.getTotalBytes() != null ? e.getTotalBytes() : 0L)
                .max()
                .orElse(1L);

        // Normalize weights
        topologyEdges.forEach(edge -> {
            double weight = maxBytes > 0 ? (double) edge.getTotalBytes() / maxBytes : 0.5;
            edge.setWeight(Math.max(0.1, weight));
        });

        // Update node connection counts
        Map<String, Integer> connectionCounts = new HashMap<>();
        Map<String, Long> nodeBytes = new HashMap<>();
        for (NetworkTopologyDTO.TopologyEdge edge : topologyEdges) {
            connectionCounts.merge(edge.getSource(), 1, Integer::sum);
            connectionCounts.merge(edge.getTarget(), 1, Integer::sum);
            nodeBytes.merge(edge.getSource(), edge.getTotalBytes(), Long::sum);
            nodeBytes.merge(edge.getTarget(), edge.getTotalBytes(), Long::sum);
        }

        nodes.forEach(node -> {
            node.setConnectionCount(connectionCounts.getOrDefault(node.getId(), 0));
            node.setTotalBytes(nodeBytes.getOrDefault(node.getId(), 0L));
        });

        injectLogicalServiceEdges(nodes, topologyEdges, nodeIds, podLabelMap, namespaces, clusterId);

        log.info("Generated topology from flows: {} nodes, {} edges", nodes.size(), topologyEdges.size());

        return NetworkTopologyDTO.builder()
                .nodes(nodes)
                .edges(topologyEdges)
                .build();
    }

    // Private helper methods

    private void updateTopologyEdge(NetworkFlowDTO dto) {
        if (dto.getSource() == null || dto.getDestination() == null) {
            return;
        }
        // flowType null veya "unknown" ise topoloji edge'ine kaydetme
        if (dto.getFlowType() == null || "unknown".equalsIgnoreCase(dto.getFlowType())) {
            return;
        }

        try {
            String sourceType = dto.getSource().getKind() != null ? dto.getSource().getKind().toLowerCase() : "pod";
            String sourceName = dto.getSource().getPodName() != null ? dto.getSource().getPodName()
                    : dto.getSource().getIp();
            String sourceNamespace = dto.getSource().getNamespace();

            String targetType = dto.getDestination().getKind() != null ? dto.getDestination().getKind().toLowerCase()
                    : "pod";
            String targetName = dto.getDestination().getPodName() != null ? dto.getDestination().getPodName()
                    : dto.getDestination().getIp();
            String targetNamespace = dto.getDestination().getNamespace();

            String protocol = dto.getNetwork() != null ? dto.getNetwork().getProtocol() : "TCP";
            Integer port = dto.getDestination().getPort();
            Long bytes = dto.getNetwork() != null && dto.getNetwork().getBytes() != null ? dto.getNetwork().getBytes()
                    : 0L;

            // Find or create edge (use findFirst to handle potential duplicates)
            List<NetworkTopologyEdge> existingEdges = topologyRepository
                    .findByClusterUidAndSourceTypeAndSourceNameAndSourceNamespaceAndTargetTypeAndTargetNameAndTargetNamespaceAndProtocolAndPort(
                            dto.getClusterUid(), sourceType, sourceName, sourceNamespace, targetType, targetName,
                            targetNamespace, protocol, port);

            NetworkTopologyEdge edge;
            Optional<NetworkTopologyEdge> existingEdge = existingEdges.isEmpty() ? Optional.empty()
                    : Optional.of(existingEdges.get(0));
            if (existingEdge.isPresent()) {
                edge = existingEdge.get();
                edge.setFlowCount(edge.getFlowCount() + 1);
                edge.setTotalBytes(edge.getTotalBytes() + bytes);
                edge.setLastSeen(LocalDateTime.now());

                // Update service information if missing
                if (dto.getDestination().getService() != null) {
                    if (edge.getServiceName() == null) {
                        edge.setServiceName(dto.getDestination().getService().getName());
                        edge.setServiceNamespace(dto.getDestination().getService().getNamespace());
                    }
                    if (edge.getBackendPodName() == null
                            && dto.getDestination().getService().getBackendPodName() != null) {
                        edge.setBackendPodName(dto.getDestination().getService().getBackendPodName());
                    }
                    if (edge.getBackendPodNamespace() == null
                            && dto.getDestination().getService().getBackendPodNamespace() != null) {
                        edge.setBackendPodNamespace(dto.getDestination().getService().getBackendPodNamespace());
                    }
                }
            } else {
                edge = NetworkTopologyEdge.builder()
                        .clusterUid(dto.getClusterUid())
                        .sourceType(sourceType)
                        .sourceName(sourceName)
                        .sourceNamespace(sourceNamespace)
                        .targetType(targetType)
                        .targetName(targetName)
                        .targetNamespace(targetNamespace)
                        .protocol(protocol)
                        .port(port)
                        .flowCount(1L)
                        .totalBytes(bytes)
                        .lastSeen(LocalDateTime.now())
                        .build();

                edge.setFlowType(dto.getFlowType());

                // Populate service information if destination is a service
                if (dto.getDestination().getService() != null) {
                    edge.setServiceName(dto.getDestination().getService().getName());
                    edge.setServiceNamespace(dto.getDestination().getService().getNamespace());
                    edge.setBackendPodName(dto.getDestination().getService().getBackendPodName());
                    edge.setBackendPodNamespace(dto.getDestination().getService().getBackendPodNamespace());
                }
            }

            topologyRepository.save(edge);
        } catch (Exception e) {
            log.error("Failed to update topology edge for flow: {}", dto, e);
        }
    }

    private String buildNodeId(String type, String namespace, String name) {
        return type + ":" + (namespace != null ? namespace : "_") + ":" + name;
    }

    private NetworkFlow convertToEntity(NetworkFlowDTO dto) {
        NetworkFlow.NetworkFlowBuilder builder = NetworkFlow.builder()
                .flowId(dto.getFlowId() != null ? dto.getFlowId() : UUID.randomUUID().toString())
                .clusterUid(dto.getClusterUid())
                .flowType(dto.getFlowType())
                .timestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now())
                .nodeName(dto.getNodeName());

        // Source
        if (dto.getSource() != null) {
            builder.sourcePodName(dto.getSource().getPodName())
                    .sourceNamespace(dto.getSource().getNamespace())
                    .sourceKind(dto.getSource().getKind())
                    .sourceIp(dto.getSource().getIp())
                    .sourcePort(dto.getSource().getPort())
                    .sourceNodeName(dto.getSource().getNodeName())
                    .sourceIngress(dto.getSource().getIngress())
                    .sourceEgress(dto.getSource().getEgress())
                    .sourceDrop(dto.getSource().getDrop())
                    .sourcePass(dto.getSource().getPass());

            /*
             * if (dto.getSource().getPodLabels() != null) {
             * try {
             * builder.sourcePodLabels(objectMapper.writeValueAsString(dto.getSource().
             * getPodLabels()));
             * } catch (JsonProcessingException e) {
             * log.warn("Failed to serialize source pod labels", e);
             * }
             * }
             */
        }

        // Destination
        if (dto.getDestination() != null) {
            builder.destinationPodName(dto.getDestination().getPodName())
                    .destinationNamespace(dto.getDestination().getNamespace())
                    .destinationKind(dto.getDestination().getKind())
                    .destinationIp(dto.getDestination().getIp())
                    .destinationPort(dto.getDestination().getPort())
                    .destinationNodeName(dto.getDestination().getNodeName())
                    .destinationIngress(dto.getDestination().getIngress())
                    .destinationEgress(dto.getDestination().getEgress())
                    .destinationDrop(dto.getDestination().getDrop())
                    .destinationPass(dto.getDestination().getPass());

            /*
             * if (dto.getDestination().getPodLabels() != null) {
             * try {
             * builder.destinationPodLabels(objectMapper.writeValueAsString(dto.
             * getDestination().getPodLabels()));
             * } catch (JsonProcessingException e) {
             * log.warn("Failed to serialize destination pod labels", e);
             * }
             * }
             */

            // Service info
            if (dto.getDestination().getService() != null) {
                builder.serviceName(dto.getDestination().getService().getName())
                        .serviceNamespace(dto.getDestination().getService().getNamespace())
                        .backendPodName(dto.getDestination().getService().getBackendPodName())
                        .backendPodNamespace(dto.getDestination().getService().getBackendPodNamespace());

                /*
                 * if (dto.getDestination().getService().getLabels() != null) {
                 * try {
                 * builder.serviceLabels(
                 * objectMapper.writeValueAsString(dto.getDestination().getService().getLabels()
                 * ));
                 * } catch (JsonProcessingException e) {
                 * log.warn("Failed to serialize service labels", e);
                 * }
                 * }
                 * 
                 * if (dto.getDestination().getService().getBackendPodLabels() != null) {
                 * try {
                 * builder.backendPodLabels(objectMapper
                 * .writeValueAsString(dto.getDestination().getService().getBackendPodLabels()))
                 * ;
                 * } catch (JsonProcessingException e) {
                 * log.warn("Failed to serialize backend pod labels", e);
                 * }
                 * }
                 */
            }
        }

        // Network
        if (dto.getNetwork() != null) {
            builder.protocol(dto.getNetwork().getProtocol())
                    .tcpFlags(dto.getNetwork().getTcpFlags())
                    .bytes(dto.getNetwork().getBytes())
                    .interfaceName(dto.getNetwork().getInterfaceName())
                    .direction(dto.getNetwork().getDirection());
        }

        // L7
        if (dto.getL7() != null) {
            builder.l7Protocol(dto.getL7().getProtocol())
                    .l7Method(dto.getL7().getMethod())
                    .l7Host(dto.getL7().getHost())
                    .l7Url(dto.getL7().getUrl())
                    .l7Path(dto.getL7().getPath())
                    .l7StatusCode(dto.getL7().getStatusCode())
                    .l7ContentType(dto.getL7().getContentType());
        }

        return builder.build();
    }

    private NetworkFlowDTO convertToDTO(NetworkFlow flow) {
        NetworkFlowDTO.NetworkFlowDTOBuilder builder = NetworkFlowDTO.builder()
                .id(flow.getId())
                .flowId(flow.getFlowId())
                .clusterUid(flow.getClusterUid())
                .flowType(flow.getFlowType())
                .timestamp(flow.getTimestamp())
                .nodeName(flow.getNodeName());

        // Source
        NetworkFlowDTO.SourceInfoDTO source = NetworkFlowDTO.SourceInfoDTO.builder()
                .podName(flow.getSourcePodName())
                .namespace(flow.getSourceNamespace())
                .kind(flow.getSourceKind())
                .ip(flow.getSourceIp())
                .port(flow.getSourcePort())
                .nodeName(flow.getSourceNodeName())
                .ingress(flow.getSourceIngress())
                .egress(flow.getSourceEgress())
                .drop(flow.getSourceDrop())
                .pass(flow.getSourcePass())
                .build();

        Map<String, String> srcLabels = getPodLabels(flow.getClusterUid(), flow.getSourceNamespace(),
                flow.getSourcePodName());
        if (srcLabels != null) {
            source.setPodLabels(srcLabels);
        }
        builder.source(source);

        // Destination
        NetworkFlowDTO.DestinationInfoDTO destination = NetworkFlowDTO.DestinationInfoDTO.builder()
                .podName(flow.getDestinationPodName())
                .namespace(flow.getDestinationNamespace())
                .kind(flow.getDestinationKind())
                .ip(flow.getDestinationIp())
                .port(flow.getDestinationPort())
                .nodeName(flow.getDestinationNodeName())
                .ingress(flow.getDestinationIngress())
                .egress(flow.getDestinationEgress())
                .drop(flow.getDestinationDrop())
                .pass(flow.getDestinationPass())
                .build();

        Map<String, String> dstLabels = getPodLabels(flow.getClusterUid(), flow.getDestinationNamespace(),
                flow.getDestinationPodName());
        if (dstLabels != null) {
            destination.setPodLabels(dstLabels);
        }

        // Service info
        if (flow.getServiceName() != null) {
            NetworkFlowDTO.ServiceInfoDTO service = NetworkFlowDTO.ServiceInfoDTO.builder()
                    .name(flow.getServiceName())
                    .namespace(flow.getServiceNamespace())
                    .backendPodName(flow.getBackendPodName())
                    .backendPodNamespace(flow.getBackendPodNamespace())
                    .build();

            Map<String, String> svcLabels = getServiceLabels(flow.getClusterUid(), flow.getServiceNamespace(),
                    flow.getServiceName());
            if (svcLabels != null) {
                service.setLabels(svcLabels);
            }

            if (flow.getBackendPodName() != null) {
                Map<String, String> backendLabels = getPodLabels(flow.getClusterUid(), flow.getBackendPodNamespace(),
                        flow.getBackendPodName());
                if (backendLabels != null) {
                    service.setBackendPodLabels(backendLabels);
                }
            }

            destination.setService(service);
        }
        builder.destination(destination);

        // Network
        builder.network(NetworkFlowDTO.NetworkInfoDTO.builder()
                .protocol(flow.getProtocol())
                .tcpFlags(flow.getTcpFlags())
                .bytes(flow.getBytes())
                .interfaceName(flow.getInterfaceName())
                .direction(flow.getDirection())
                .build());

        // L7
        if (flow.getL7Protocol() != null) {
            builder.l7(NetworkFlowDTO.L7InfoDTO.builder()
                    .protocol(flow.getL7Protocol())
                    .method(flow.getL7Method())
                    .host(flow.getL7Host())
                    .url(flow.getL7Url())
                    .path(flow.getL7Path())
                    .statusCode(flow.getL7StatusCode())
                    .contentType(flow.getL7ContentType())
                    .build());
        }

        return builder.build();
    }

    private Pageable createPageable(NetworkFlowFilterDTO filter) {
        int page = filter.getPage() != null ? filter.getPage() : 0;
        int size = filter.getPageSize() != null ? filter.getPageSize() : 50;
        String sortBy = filter.getSortBy() != null ? filter.getSortBy() : "timestamp";
        boolean desc = filter.getSortDesc() != null ? filter.getSortDesc() : true;

        Sort sort = desc ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        return PageRequest.of(page, size, sort);
    }

    private Specification<NetworkFlow> createSpecification(NetworkFlowFilterDTO filter) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (filter.getClusterUid() != null) {
                predicates.add(cb.equal(root.get("clusterUid"), filter.getClusterUid()));
            }

            if (filter.getFlowTypes() != null && !filter.getFlowTypes().isEmpty()) {
                predicates.add(root.get("flowType").in(filter.getFlowTypes()));
            }

            // Namespace filtering with OR logic - show flows where source OR destination is
            // in the namespace
            boolean hasSourceNs = filter.getSourceNamespaces() != null && !filter.getSourceNamespaces().isEmpty();
            boolean hasDestNs = filter.getDestinationNamespaces() != null
                    && !filter.getDestinationNamespaces().isEmpty();

            if (hasSourceNs && hasDestNs) {
                // If both are provided, use OR: source IN (...) OR destination IN (...)
                predicates.add(cb.or(
                        root.get("sourceNamespace").in(filter.getSourceNamespaces()),
                        root.get("destinationNamespace").in(filter.getDestinationNamespaces())));
            } else if (hasSourceNs) {
                predicates.add(root.get("sourceNamespace").in(filter.getSourceNamespaces()));
            } else if (hasDestNs) {
                predicates.add(root.get("destinationNamespace").in(filter.getDestinationNamespaces()));
            }

            if (filter.getSourcePodName() != null && !filter.getSourcePodName().isEmpty()) {
                predicates.add(cb.like(root.get("sourcePodName"), "%" + filter.getSourcePodName() + "%"));
            }

            if (filter.getDestinationPodName() != null && !filter.getDestinationPodName().isEmpty()) {
                predicates.add(cb.like(root.get("destinationPodName"), "%" + filter.getDestinationPodName() + "%"));
            }

            if (filter.getProtocols() != null && !filter.getProtocols().isEmpty()) {
                predicates.add(root.get("protocol").in(filter.getProtocols()));
            }

            if (filter.getSourceIp() != null && !filter.getSourceIp().isEmpty()) {
                predicates.add(cb.equal(root.get("sourceIp"), filter.getSourceIp()));
            }

            if (filter.getDestinationIp() != null && !filter.getDestinationIp().isEmpty()) {
                predicates.add(cb.equal(root.get("destinationIp"), filter.getDestinationIp()));
            }

            if (filter.getSourcePort() != null) {
                predicates.add(cb.equal(root.get("sourcePort"), filter.getSourcePort()));
            }

            if (filter.getDestinationPort() != null) {
                predicates.add(cb.equal(root.get("destinationPort"), filter.getDestinationPort()));
            }

            if (filter.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), filter.getStartTime()));
            }

            if (filter.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), filter.getEndTime()));
            }

            if (filter.getL7Method() != null && !filter.getL7Method().isEmpty()) {
                predicates.add(cb.equal(root.get("l7Method"), filter.getL7Method()));
            }

            if (filter.getL7Path() != null && !filter.getL7Path().isEmpty()) {
                predicates.add(cb.like(root.get("l7Path"), "%" + filter.getL7Path() + "%"));
            }

            if (filter.getServiceName() != null && !filter.getServiceName().isEmpty()) {
                predicates.add(cb.like(root.get("serviceName"), "%" + filter.getServiceName() + "%"));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private List<NetworkFlowStatsDTO.StatItem> convertToStatItems(List<Object[]> rows, long total) {
        return rows.stream()
                .map(row -> {
                    String key = row[0] != null ? row[0].toString() : "unknown";
                    long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                    long bytes = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                    double percentage = total > 0 ? (double) count / total * 100 : 0;

                    return NetworkFlowStatsDTO.StatItem.builder()
                            .key(key)
                            .count(count)
                            .bytes(bytes)
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Map<String, String> getPodLabels(String clusterUid, String namespace, String podName) {
        if (clusterUid == null || namespace == null || podName == null)
            return null;
        try {
            Long clusterId = getClusterId(clusterUid);
            if (clusterId == null)
                return null;

            return podRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, podName)
                    .map(pod -> {
                        try {
                            return objectMapper.readValue(pod.getLabels(), new TypeReference<Map<String, String>>() {
                            });
                        } catch (JsonProcessingException e) {
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Error fetching labels for pod {}/{}: {}", namespace, podName, e.getMessage());
            return null;
        }
    }

    private String getTopologyKey(NetworkFlowDTO dto) {
        if (dto.getSource() == null || dto.getDestination() == null) {
            return "unknown";
        }
        return String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s",
                dto.getClusterUid(),
                dto.getSource().getKind() != null ? dto.getSource().getKind().toLowerCase() : "pod",
                dto.getSource().getPodName() != null ? dto.getSource().getPodName() : dto.getSource().getIp(),
                dto.getSource().getNamespace(),
                dto.getDestination().getKind() != null ? dto.getDestination().getKind().toLowerCase() : "pod",
                dto.getDestination().getPodName() != null ? dto.getDestination().getPodName()
                        : dto.getDestination().getIp(),
                dto.getDestination().getNamespace(),
                dto.getNetwork() != null ? dto.getNetwork().getProtocol() : "TCP",
                dto.getDestination().getPort());
    }

    private void updateTopologyEdgeAggregated(TopologyAggregation agg) {
        NetworkFlowDTO dto = agg.getSampleDto();
        String sourceType = dto.getSource().getKind() != null ? dto.getSource().getKind().toLowerCase() : "pod";
        String sourceName = dto.getSource().getPodName() != null ? dto.getSource().getPodName()
                : dto.getSource().getIp();
        String sourceNamespace = dto.getSource().getNamespace();

        String targetType = dto.getDestination().getKind() != null ? dto.getDestination().getKind().toLowerCase()
                : "pod";
        String targetName = dto.getDestination().getPodName() != null ? dto.getDestination().getPodName()
                : dto.getDestination().getIp();
        String targetNamespace = dto.getDestination().getNamespace();

        String protocol = dto.getNetwork() != null ? dto.getNetwork().getProtocol() : "TCP";
        Integer port = dto.getDestination().getPort();

        List<NetworkTopologyEdge> existingEdges = topologyRepository
                .findByClusterUidAndSourceTypeAndSourceNameAndSourceNamespaceAndTargetTypeAndTargetNameAndTargetNamespaceAndProtocolAndPort(
                        dto.getClusterUid(), sourceType, sourceName, sourceNamespace, targetType, targetName,
                        targetNamespace, protocol, port);

        NetworkTopologyEdge edge;
        if (!existingEdges.isEmpty()) {
            edge = existingEdges.get(0);
            edge.setFlowCount(edge.getFlowCount() + agg.getFlowCount());
            edge.setTotalBytes(edge.getTotalBytes() + agg.getTotalBytes());
            edge.setLastSeen(LocalDateTime.now());

            // Update service information if missing
            if (dto.getDestination().getService() != null) {
                if (edge.getServiceName() == null) {
                    edge.setServiceName(dto.getDestination().getService().getName());
                    edge.setServiceNamespace(dto.getDestination().getService().getNamespace());
                }
                if (edge.getBackendPodName() == null
                        && dto.getDestination().getService().getBackendPodName() != null) {
                    edge.setBackendPodName(dto.getDestination().getService().getBackendPodName());
                }
                if (edge.getBackendPodNamespace() == null
                        && dto.getDestination().getService().getBackendPodNamespace() != null) {
                    edge.setBackendPodNamespace(dto.getDestination().getService().getBackendPodNamespace());
                }
            }
        } else {
            edge = NetworkTopologyEdge.builder()
                    .clusterUid(dto.getClusterUid())
                    .sourceType(sourceType)
                    .sourceName(sourceName)
                    .sourceNamespace(sourceNamespace)
                    .targetType(targetType)
                    .targetName(targetName)
                    .targetNamespace(targetNamespace)
                    .protocol(protocol)
                    .port(port)
                    .flowCount(agg.getFlowCount())
                    .totalBytes(agg.getTotalBytes())
                    .lastSeen(LocalDateTime.now())
                    .build();

            edge.setFlowType(dto.getFlowType());

            if (dto.getDestination().getService() != null) {
                edge.setServiceName(dto.getDestination().getService().getName());
                edge.setServiceNamespace(dto.getDestination().getService().getNamespace());
                edge.setBackendPodName(dto.getDestination().getService().getBackendPodName());
                edge.setBackendPodNamespace(dto.getDestination().getService().getBackendPodNamespace());
            }
        }

        topologyRepository.save(edge);
    }

    private static class TopologyAggregation {
        private final NetworkFlowDTO sampleDto;
        private long flowCount = 0;
        private long totalBytes = 0;

        public TopologyAggregation(NetworkFlowDTO sampleDto) {
            this.sampleDto = sampleDto;
        }

        public void addFlow(NetworkFlowDTO dto) {
            this.flowCount++;
            if (dto.getNetwork() != null && dto.getNetwork().getBytes() != null) {
                this.totalBytes += dto.getNetwork().getBytes();
            }
        }

        public NetworkFlowDTO getSampleDto() {
            return sampleDto;
        }

        public long getFlowCount() {
            return flowCount;
        }

        public long getTotalBytes() {
            return totalBytes;
        }
    }

    private Map<String, String> getServiceLabels(String clusterUid, String namespace, String serviceName) {
        if (clusterUid == null || namespace == null || serviceName == null)
            return null;
        try {
            Long clusterId = getClusterId(clusterUid);
            if (clusterId == null)
                return null;

            return serviceRepository
                    .findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(clusterId, namespace, serviceName)
                    .map(svc -> {
                        try {
                            return objectMapper.readValue(svc.getLabels(), new TypeReference<Map<String, String>>() {
                            });
                        } catch (JsonProcessingException e) {
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Error fetching labels for service {}/{}: {}", namespace, serviceName, e.getMessage());
            return null;
        }
    }

    private Long getClusterId(String clusterUid) {
        return clusterRepository.findByUid(clusterUid).map(com.k8s.platform.domain.entity.Cluster::getId).orElse(null);
    }

    private void injectLogicalServiceEdges(
            List<NetworkTopologyDTO.TopologyNode> nodes,
            List<NetworkTopologyDTO.TopologyEdge> edges,
            Set<String> nodeIds,
            Map<String, Map<String, String>> podLabelMap,
            List<String> namespaces,
            Long clusterId) {

        if (clusterId == null)
            return;

        List<com.k8s.platform.domain.entity.k8s.Service> clusterServices = new ArrayList<>();
        if (namespaces != null && !namespaces.isEmpty()) {
            for (String ns : namespaces) {
                clusterServices.addAll(serviceRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(clusterId, ns));
            }
        } else {
            clusterServices = serviceRepository.findByClusterIdAndIsDeletedFalse(clusterId);
        }

        long logicalEdgeIdCounter = -1000L;

        for (com.k8s.platform.domain.entity.k8s.Service svc : clusterServices) {
            if (svc.getSelector() != null && !svc.getSelector().isBlank() && !svc.getSelector().trim().equals("{}")) {
                try {
                    Map<String, String> selectors = objectMapper.readValue(svc.getSelector(),
                            new TypeReference<Map<String, String>>() {
                            });
                    if (selectors.isEmpty())
                        continue;

                    String svcId = buildNodeId("service", svc.getNamespace(), svc.getName());

                    for (Map.Entry<String, Map<String, String>> podEntry : podLabelMap.entrySet()) {
                        String podKey = podEntry.getKey();
                        String[] parts = podKey.split(":");
                        String podNamespace = parts[0];
                        String podName = parts[1];
                        Map<String, String> podLabels = podEntry.getValue();

                        if (!svc.getNamespace().equals(podNamespace))
                            continue;
                        if (podLabels == null || podLabels.isEmpty())
                            continue;

                        boolean matches = true;

                        // K8s Services usually just have a flat map for selector, but handle possible
                        // 'matchLabels' nesting
                        Map<String, String> labelsToCheck = selectors;
                        if (selectors.containsKey("matchLabels") && selectors.size() == 1) {
                            try {
                                Object nested = objectMapper
                                        .readValue(svc.getSelector(), new TypeReference<Map<String, Object>>() {
                                        }).get("matchLabels");
                                if (nested instanceof Map) {
                                    labelsToCheck = (Map<String, String>) nested;
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        for (Map.Entry<String, String> sel : labelsToCheck.entrySet()) {
                            if (!sel.getValue().equals(podLabels.get(sel.getKey()))) {
                                matches = false;
                                break;
                            }
                        }

                        if (matches) {
                            String tgtId = buildNodeId("pod", podNamespace, podName);

                            if (!nodeIds.contains(svcId)) {
                                nodeIds.add(svcId);
                                nodes.add(NetworkTopologyDTO.TopologyNode.builder()
                                        .id(svcId)
                                        .name(svc.getName())
                                        .namespace(svc.getNamespace())
                                        .type("service")
                                        .label(svc.getName())
                                        .group(svc.getNamespace())
                                        .connectionCount(0)
                                        .totalBytes(0L)
                                        .build());
                            }

                            if (!nodeIds.contains(tgtId)) {
                                nodeIds.add(tgtId);
                                nodes.add(NetworkTopologyDTO.TopologyNode.builder()
                                        .id(tgtId)
                                        .name(podName)
                                        .namespace(podNamespace)
                                        .type("pod")
                                        .label(podName)
                                        .group(podNamespace)
                                        .connectionCount(0)
                                        .totalBytes(0L)
                                        .podLabels(podLabels)
                                        .build());
                            }

                            edges.add(NetworkTopologyDTO.TopologyEdge.builder()
                                    .id(logicalEdgeIdCounter--)
                                    .source(svcId)
                                    .target(tgtId)
                                    .protocol("TCP")
                                    .port(80)
                                    .flowCount(1L)
                                    .totalBytes(0L)
                                    .lastSeen(java.time.LocalDateTime.now())
                                    .label("backend")
                                    .weight(0.1)
                                    .isLogical(true)
                                    .build());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse selectors for service {}", svc.getName());
                }
            }
        }
    }
}
