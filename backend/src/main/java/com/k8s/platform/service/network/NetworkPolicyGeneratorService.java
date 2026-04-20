package com.k8s.platform.service.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.config.NetworkPolicyConfig;
import com.k8s.platform.domain.entity.network.GeneratedNetworkPolicy;
import com.k8s.platform.domain.entity.network.NetworkTopologyEdge;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.entity.k8s.Pod;
import com.k8s.platform.domain.repository.k8s.PodRepository;
import com.k8s.platform.domain.repository.k8s.ServiceRepository;
import com.k8s.platform.domain.repository.network.GeneratedNetworkPolicyRepository;
import com.k8s.platform.domain.repository.network.NetworkFlowRepository;
import com.k8s.platform.domain.repository.network.NetworkTopologyEdgeRepository;
import com.k8s.platform.dto.network.*;
import com.k8s.platform.service.cluster.ClusterContextManager;
import com.k8s.platform.service.k8s.NetworkPolicyService;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkPolicyGeneratorService {

    private final NetworkFlowRepository flowRepository;
    private final NetworkTopologyEdgeRepository topologyEdgeRepository;
    private final GeneratedNetworkPolicyRepository generatedPolicyRepository;
    private final PodRepository podRepository;
    private final ServiceRepository serviceRepository;
    private final ClusterRepository clusterRepository;
    private final PolicyMigrationService migrationService;
    private final PolicyConflictService conflictService;
    private final ClusterContextManager clusterContextManager;
    private final NetworkPolicyService networkPolicyService;
    private final ObjectMapper objectMapper;
    private final NetworkPolicyConfig networkPolicyConfig;

    /**
     * Extract distinct traffic rules from network topology edges for a specific
     * source
     * Optimized version using topology edges instead of individual flows
     */
    public List<NetworkPolicyRuleDTO> extractDistinctRules(
            String clusterUid,
            String namespace,
            String sourcePodName,
            String direction) {

        log.info("Extracting distinct rules for cluster: {}, namespace: {}, pod: {}, direction: {}",
                clusterUid, namespace, sourcePodName, direction);

        // Use topology edges instead of flows for better performance
        // Use direction-specific queries for optimal index usage
        List<NetworkTopologyEdge> edges;
        if ("ingress".equalsIgnoreCase(direction)) {
            edges = topologyEdgeRepository.findByClusterUidAndTargetNamespaceForIngress(clusterUid, namespace);
        } else {
            edges = topologyEdgeRepository.findByClusterUidAndSourceNamespaceForEgress(clusterUid, namespace);
        }

        // Filter by pod name if specified
        if (sourcePodName != null && !sourcePodName.isEmpty()) {
            edges = edges.stream()
                    .filter(edge -> {
                        if ("ingress".equalsIgnoreCase(direction)) {
                            return edge.getTargetName() != null && edge.getTargetName().contains(sourcePodName);
                        } else {
                            return edge.getSourceName() != null && edge.getSourceName().contains(sourcePodName);
                        }
                    })
                    .collect(Collectors.toList());
        }

        // Convert topology edges to rules
        // Build rule first, then compute normalized key so that pod-to-service
        // and pod-to-pod edges for the same backend are merged
        Map<String, NetworkPolicyRuleDTO> ruleMap = new HashMap<>();

        for (NetworkTopologyEdge edge : edges) {
            NetworkPolicyRuleDTO rule = buildRuleFromEdge(edge, direction);
            String ruleKey = buildNormalizedRuleKey(rule, direction);

            if (!ruleMap.containsKey(ruleKey)) {
                rule.setRuleId(ruleKey);
                rule.setFlowCount(edge.getFlowCount() != null ? edge.getFlowCount() : 1L);
                rule.setTotalBytes(edge.getTotalBytes() != null ? edge.getTotalBytes() : 0L);
                ruleMap.put(ruleKey, rule);
            } else {
                // Aggregate if multiple edges map to same rule
                NetworkPolicyRuleDTO existing = ruleMap.get(ruleKey);
                existing.setFlowCount(
                        existing.getFlowCount() + (edge.getFlowCount() != null ? edge.getFlowCount() : 1L));
                existing.setTotalBytes(
                        existing.getTotalBytes() + (edge.getTotalBytes() != null ? edge.getTotalBytes() : 0L));
            }
        }

        List<NetworkPolicyRuleDTO> rules = new ArrayList<>(ruleMap.values());
        rules.sort((a, b) -> Long.compare(b.getFlowCount(), a.getFlowCount()));

        log.info("Extracted {} distinct rules from {} topology edges", rules.size(), edges.size());
        return rules;
    }

    /**
     * Get configured policy label keys
     */
    public List<String> getPolicyLabelKeys() {
        return networkPolicyConfig.getPolicyLabelsList();
    }

    /**
     * Get workload labels for policy targeting based on configured policy labels
     * Optimized version using topology edges instead of individual flows
     */
    public List<PolicyLabelDTO> getWorkloadLabels(String clusterUid, String namespace, String direction) {
        log.info("Getting workload labels for cluster: {}, namespace: {}, direction: {}",
                clusterUid, namespace, direction);

        List<String> policyLabelKeys = networkPolicyConfig.getPolicyLabelsList();

        // Use topology edges instead of flows for better performance
        // Use direction-specific queries for optimal index usage
        List<NetworkTopologyEdge> edges;
        if ("ingress".equalsIgnoreCase(direction)) {
            edges = topologyEdgeRepository.findByClusterUidAndTargetNamespaceForIngress(clusterUid, namespace);
        } else {
            edges = topologyEdgeRepository.findByClusterUidAndSourceNamespaceForEgress(clusterUid, namespace);
        }

        // Map to aggregate labels: key -> value -> (flowCount, podNames, serviceName)
        Map<String, Map<String, LabelAggregation>> labelMap = new HashMap<>();

        for (NetworkTopologyEdge edge : edges) {
            Map<String, String> labels = null;
            String podName = null;
            String serviceName = null;
            boolean fromService = false;

            if ("ingress".equalsIgnoreCase(direction)) {
                // For ingress, target is destination
                podName = edge.getTargetName();
                labels = getPodLabels(edge.getClusterUid(), edge.getTargetNamespace(), podName);

                // If destination is a service, use backend labels
                if (edge.getBackendPodName() != null) {
                    Map<String, String> backendLabels = getPodLabels(edge.getClusterUid(),
                            edge.getBackendPodNamespace(), edge.getBackendPodName());
                    if (backendLabels != null && !backendLabels.isEmpty()) {
                        labels = backendLabels;
                        serviceName = edge.getServiceName();
                        fromService = true;
                    }
                }
            } else {
                // For egress, target is source
                podName = edge.getSourceName();
                labels = getPodLabels(edge.getClusterUid(), edge.getSourceNamespace(), podName);
            }

            if (labels == null || labels.isEmpty())
                continue;

            // Find matching policy labels
            for (String labelKey : policyLabelKeys) {
                String labelValue = labels.get(labelKey);
                if (labelValue != null && !labelValue.isEmpty()) {
                    LabelAggregation agg = labelMap.computeIfAbsent(labelKey, k -> new HashMap<>())
                            .computeIfAbsent(labelValue, v -> new LabelAggregation());
                    // Use flow count from edge instead of incrementing by 1
                    agg.addFlowsFromEdge(podName, serviceName, fromService,
                            edge.getFlowCount() != null ? edge.getFlowCount() : 1L);
                }
            }
        }

        // Convert to DTOs
        List<PolicyLabelDTO> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, LabelAggregation>> keyEntry : labelMap.entrySet()) {
            String labelKey = keyEntry.getKey();
            for (Map.Entry<String, LabelAggregation> valueEntry : keyEntry.getValue().entrySet()) {
                String labelValue = valueEntry.getKey();
                LabelAggregation agg = valueEntry.getValue();

                result.add(PolicyLabelDTO.builder()
                        .labelKey(labelKey)
                        .labelValue(labelValue)
                        .displayName(labelKey + "=" + labelValue)
                        .flowCount(agg.flowCount)
                        .podNames(new ArrayList<>(agg.podNames))
                        .fromService(agg.fromService)
                        .serviceName(agg.serviceName)
                        .build());
            }
        }

        // Sort by flow count descending
        result.sort((a, b) -> Long.compare(b.getFlowCount(), a.getFlowCount()));

        log.info("Found {} unique workload labels", result.size());
        return result;
    }

    /**
     * Extract distinct traffic rules filtered by a specific label
     * Optimized version using topology edges with label filtering
     */
    public List<NetworkPolicyRuleDTO> extractDistinctRulesByLabel(
            String clusterUid,
            String namespace,
            String labelKey,
            String labelValue,
            String direction) {

        log.info("Extracting distinct rules by label for cluster: {}, namespace: {}, label: {}={}, direction: {}",
                clusterUid, namespace, labelKey, labelValue, direction);

        Long clusterId = getClusterId(clusterUid);
        if (clusterId == null) {
            return Collections.emptyList();
        }

        // 1. Find all pods matching the labels in this namespace
        String labelPattern = String.format("\"%s\":\"%s\"", labelKey, labelValue);
        List<Pod> matchingPods = podRepository
                .findByClusterIdAndNamespaceAndLabelsContainingAndIsDeletedFalse(clusterId, namespace, labelPattern);

        if (matchingPods.isEmpty()) {
            log.info("No pods found with label {}={} in namespace {}", labelKey, labelValue, namespace);
            return Collections.emptyList();
        }

        List<String> podNames = matchingPods.stream()
                .map(com.k8s.platform.domain.entity.k8s.Pod::getName)
                .collect(Collectors.toList());

        // 2. Fetch topology edges based on matching pod names
        List<NetworkTopologyEdge> edges;
        if ("ingress".equalsIgnoreCase(direction)) {
            edges = topologyEdgeRepository.findIngressEdgesByPodNames(clusterUid, namespace, podNames);
        } else {
            edges = topologyEdgeRepository.findEgressEdgesByPodNames(clusterUid, namespace, podNames);
        }

        // Convert topology edges to rules
        Map<String, NetworkPolicyRuleDTO> ruleMap = new HashMap<>();

        for (NetworkTopologyEdge edge : edges) {
            NetworkPolicyRuleDTO rule = buildRuleFromEdge(edge, direction);
            String ruleKey = buildNormalizedRuleKey(rule, direction);

            if (!ruleMap.containsKey(ruleKey)) {
                rule.setRuleId(ruleKey);
                rule.setFlowCount(edge.getFlowCount() != null ? edge.getFlowCount() : 1L);
                rule.setTotalBytes(edge.getTotalBytes() != null ? edge.getTotalBytes() : 0L);
                ruleMap.put(ruleKey, rule);
            } else {
                NetworkPolicyRuleDTO existing = ruleMap.get(ruleKey);
                existing.setFlowCount(
                        existing.getFlowCount() + (edge.getFlowCount() != null ? edge.getFlowCount() : 1L));
                existing.setTotalBytes(
                        existing.getTotalBytes() + (edge.getTotalBytes() != null ? edge.getTotalBytes() : 0L));
            }
        }

        List<NetworkPolicyRuleDTO> rules = new ArrayList<>(ruleMap.values());
        rules.sort((a, b) -> Long.compare(b.getFlowCount(), a.getFlowCount()));

        log.info("Extracted {} distinct rules from {} topology edges for label {}={}",
                rules.size(), edges.size(), labelKey, labelValue);
        return rules;
    }

    // Helper class for label aggregation
    private static class LabelAggregation {
        long flowCount = 0;
        Set<String> podNames = new HashSet<>();
        boolean fromService = false;
        String serviceName = null;

        void addFlow(String podName, String svcName, boolean isSvc) {
            flowCount++;
            if (podName != null)
                podNames.add(podName);
            if (isSvc) {
                fromService = true;
                serviceName = svcName;
            }
        }

        void addFlowsFromEdge(String podName, String svcName, boolean isSvc, long count) {
            flowCount += count;
            if (podName != null)
                podNames.add(podName);
            if (isSvc) {
                fromService = true;
                serviceName = svcName;
            }
        }
    }

    private Integer resolveTargetPort(String clusterUid, String namespace, String serviceName, Integer servicePort) {
        if (clusterUid == null || namespace == null || serviceName == null || servicePort == null) {
            log.info(
                    "resolveTargetPort: Missing parameters - clusterUid={}, namespace={}, serviceName={}, servicePort={}",
                    clusterUid, namespace, serviceName, servicePort);
            return servicePort;
        }

        log.info("Resolving targetPort for service {}/{} with servicePort {} in cluster {}",
                namespace, serviceName, servicePort, clusterUid);

        try {
            KubernetesClient client = clusterContextManager.getClient(clusterUid);
            io.fabric8.kubernetes.api.model.Service svc = client.services()
                    .inNamespace(namespace)
                    .withName(serviceName)
                    .get();

            if (svc == null) {
                log.warn("Service {}/{} not found in cluster {}", namespace, serviceName, clusterUid);
                return servicePort;
            }

            if (svc.getSpec() == null || svc.getSpec().getPorts() == null) {
                log.warn("Service {}/{} has no ports defined", namespace, serviceName);
                return servicePort;
            }

            log.info("Service {}/{} has {} ports defined", namespace, serviceName, svc.getSpec().getPorts().size());

            Integer resolvedPort = svc.getSpec().getPorts().stream()
                    .filter(p -> {
                        boolean matches = servicePort.equals(p.getPort());
                        log.debug("Checking port: {} (matches: {}), targetPort: {}",
                                p.getPort(), matches, p.getTargetPort());
                        return matches;
                    })
                    .map(p -> {
                        if (p.getTargetPort() != null) {
                            if (p.getTargetPort().getIntVal() != null) {
                                log.info("Found targetPort (int): {} for service {}/{} port {}",
                                        p.getTargetPort().getIntVal(), namespace, serviceName, servicePort);
                                return p.getTargetPort().getIntVal();
                            } else if (p.getTargetPort().getStrVal() != null) {
                                log.warn(
                                        "Found named targetPort: {} for service {}/{} - using service port as fallback",
                                        p.getTargetPort().getStrVal(), namespace, serviceName);
                                return null;
                            }
                        }
                        log.debug("No targetPort defined for service {}/{} port {}, using service port",
                                namespace, serviceName, servicePort);
                        return p.getPort();
                    })
                    .findFirst()
                    .orElse(servicePort);

            log.info("Resolved port for service {}/{}: {} -> {}", namespace, serviceName, servicePort, resolvedPort);
            return resolvedPort;

        } catch (Exception e) {
            log.error("Failed to resolve targetPort for service {}/{} in cluster {}: {}",
                    namespace, serviceName, clusterUid, e.getMessage(), e);
        }
        return servicePort;
    }

    /**
     * Generate a Network Policy from selected rules
     */
    @Transactional
    public GeneratedNetworkPolicyDTO generatePolicy(
            String clusterUid,
            GeneratePolicyRequestDTO request,
            String username) {

        log.info("Generating {} policy for namespace: {}", request.getPolicyType(), request.getNamespace());

        // Generate policy name if not provided
        String policyName = request.getName();
        if (policyName == null || policyName.isEmpty()) {
            policyName = generatePolicyName(request.getNamespace(), request.getPolicyType(), request.getPodSelector());
        }

        // Check if policy with same name exists; allow reuse if prior record is soft-deleted
        Optional<GeneratedNetworkPolicy> existingOpt = generatedPolicyRepository
                .findByClusterUidAndNamespaceAndName(clusterUid, request.getNamespace(), policyName);
        if (existingOpt.isPresent()) {
            GeneratedNetworkPolicy existing = existingOpt.get();
            if (!"deleted".equalsIgnoreCase(existing.getStatus())) {
                throw new RuntimeException("Policy with name '" + policyName + "' already exists in namespace '" +
                        request.getNamespace() + "'");
            }
            // Hard-delete the prior soft-deleted record so the name can be reused cleanly.
            // Cascade on network_policy_migrations.policy_id removes its migration history.
            generatedPolicyRepository.delete(existing);
            generatedPolicyRepository.flush();
        }

        // Build the NetworkPolicy spec
        NetworkPolicy k8sPolicy = buildNetworkPolicy(
                policyName,
                request.getNamespace(),
                request.getPolicyType(),
                request.getPodSelector(),
                request.getSelectedRules());

        // Convert to YAML
        String yamlContent = convertToYaml(k8sPolicy);

        // Save to database
        GeneratedNetworkPolicy entity = GeneratedNetworkPolicy.builder()
                .clusterUid(clusterUid)
                .namespace(request.getNamespace())
                .name(policyName)
                .policyType(request.getPolicyType())
                .podSelector(serializeMap(request.getPodSelector()))
                .rules(serializeRules(request.getSelectedRules()))
                .yamlContent(yamlContent)
                .status("draft")
                .description(request.getDescription())
                .createdBy(username)
                .build();

        entity = generatedPolicyRepository.save(entity);

        // Record migration
        migrationService.recordMigration(entity, "create", null, yamlContent,
                "Initial policy creation", username);

        // Auto-apply if requested
        if (request.isAutoApply()) {
            return applyPolicy(clusterUid, entity.getId(), username);
        }

        return convertToDTO(entity);
    }

    /**
     * Apply a generated policy to the Kubernetes cluster
     */
    @Transactional
    public GeneratedNetworkPolicyDTO applyPolicy(String clusterUid, Long policyId, String username) {
        GeneratedNetworkPolicy policy = generatedPolicyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        if (!policy.getClusterUid().equals(clusterUid)) {
            throw new RuntimeException("Policy does not belong to this cluster");
        }

        try {
            // Parse YAML and create in cluster
            KubernetesClient client = clusterContextManager.getClient(clusterUid);
            NetworkPolicy k8sPolicy = client.network().v1().networkPolicies()
                    .load(new java.io.ByteArrayInputStream(policy.getYamlContent().getBytes()))
                    .item();

            // Check if already exists
            NetworkPolicy existing = client.network().v1().networkPolicies()
                    .inNamespace(policy.getNamespace())
                    .withName(policy.getName())
                    .get();

            String previousYaml = null;
            if (existing != null) {
                previousYaml = convertToYaml(existing);
                // Update existing
                client.network().v1().networkPolicies()
                        .inNamespace(policy.getNamespace())
                        .withName(policy.getName())
                        .replace(k8sPolicy);
            } else {
                // Create new
                client.network().v1().networkPolicies()
                        .inNamespace(policy.getNamespace())
                        .resource(k8sPolicy)
                        .create();
            }

            // Update status
            policy.setStatus("applied");
            policy.setAppliedAt(LocalDateTime.now());
            policy = generatedPolicyRepository.save(policy);

            // Record migration
            migrationService.recordMigration(policy, existing != null ? "update" : "apply",
                    previousYaml, policy.getYamlContent(),
                    existing != null ? "Policy updated" : "Policy applied to cluster", username);

            log.info("Successfully applied policy {} to cluster {}", policy.getName(), clusterUid);
            return convertToDTO(policy);

        } catch (Exception e) {
            log.error("Failed to apply policy: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to apply policy: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a network policy
     */
    @Transactional
    public void deletePolicy(String clusterUid, Long policyId, boolean deleteFromCluster, String username) {
        GeneratedNetworkPolicy policy = generatedPolicyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        if (!policy.getClusterUid().equals(clusterUid)) {
            throw new RuntimeException("Policy does not belong to this cluster");
        }

        String previousYaml = policy.getYamlContent();

        // Delete from cluster if requested and policy was applied
        if (deleteFromCluster && "applied".equals(policy.getStatus())) {
            try {
                KubernetesClient client = clusterContextManager.getClient(clusterUid);
                client.network().v1().networkPolicies()
                        .inNamespace(policy.getNamespace())
                        .withName(policy.getName())
                        .delete();
                log.info("Deleted policy {} from cluster {}", policy.getName(), clusterUid);
            } catch (Exception e) {
                log.warn("Failed to delete policy from cluster: {}", e.getMessage());
            }
        }

        // Record migration before marking as deleted
        migrationService.recordMigration(policy, "delete", previousYaml, null,
                "Policy deleted", username);

        // Mark as deleted (soft delete)
        policy.setStatus("deleted");
        generatedPolicyRepository.save(policy);

        log.info("Policy {} marked as deleted", policy.getName());
    }

    /**
     * Get all generated policies for a cluster
     */
    public List<GeneratedNetworkPolicyDTO> getPolicies(String clusterUid, String namespace, String status) {
        List<GeneratedNetworkPolicy> policies;

        if (namespace != null && status != null) {
            policies = generatedPolicyRepository.findByClusterUidAndNamespaceAndStatus(clusterUid, namespace, status);
        } else if (namespace != null) {
            policies = generatedPolicyRepository.findByClusterUidAndNamespace(clusterUid, namespace);
        } else if (status != null) {
            policies = generatedPolicyRepository.findByClusterUidAndStatus(clusterUid, status);
        } else {
            policies = generatedPolicyRepository.findByClusterUid(clusterUid);
        }

        return policies.stream()
                .filter(p -> !"deleted".equals(p.getStatus()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get a single policy by ID
     */
    public GeneratedNetworkPolicyDTO getPolicy(String clusterUid, Long policyId) {
        GeneratedNetworkPolicy policy = generatedPolicyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        if (!policy.getClusterUid().equals(clusterUid)) {
            throw new RuntimeException("Policy does not belong to this cluster");
        }

        return convertToDTO(policy);
    }

    /**
     * Update policy YAML content
     */
    @Transactional
    public GeneratedNetworkPolicyDTO updatePolicy(
            String clusterUid,
            Long policyId,
            String newYamlContent,
            String description,
            String username) {

        GeneratedNetworkPolicy policy = generatedPolicyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        if (!policy.getClusterUid().equals(clusterUid)) {
            throw new RuntimeException("Policy does not belong to this cluster");
        }

        String previousYaml = policy.getYamlContent();

        // Validate YAML
        try {
            Yaml yaml = new Yaml();
            yaml.load(newYamlContent);
        } catch (Exception e) {
            throw new RuntimeException("Invalid YAML content: " + e.getMessage());
        }

        policy.setYamlContent(newYamlContent);
        if (description != null) {
            policy.setDescription(description);
        }

        // If policy was applied, mark as draft since it needs to be re-applied
        if ("applied".equals(policy.getStatus())) {
            policy.setStatus("draft");
        }

        policy = generatedPolicyRepository.save(policy);

        // Record migration
        migrationService.recordMigration(policy, "update", previousYaml, newYamlContent,
                description != null ? description : "Policy content updated", username);

        return convertToDTO(policy);
    }

    // Private helper methods

    // Helper methods for topology edge-based rule extraction

    private String buildRuleKeyFromEdge(NetworkTopologyEdge edge, String direction) {
        Integer port = edge.getPort() != null ? edge.getPort() : 0;
        String protocol = edge.getProtocol() != null ? edge.getProtocol() : "TCP";
        String sourceName = edge.getSourceName() != null ? edge.getSourceName() : "unknown";
        String targetName = edge.getTargetName() != null ? edge.getTargetName() : "unknown";

        if ("ingress".equalsIgnoreCase(direction)) {
            return String.format("%s|%s|%s|%d|%s",
                    edge.getSourceNamespace() != null ? edge.getSourceNamespace() : "_external",
                    sourceName,
                    targetName,
                    port,
                    protocol);
        } else {
            return String.format("%s|%s|%s|%d|%s",
                    sourceName,
                    edge.getTargetNamespace() != null ? edge.getTargetNamespace() : "_external",
                    targetName,
                    port,
                    protocol);
        }
    }

    /**
     * Build a normalized rule key from a fully resolved NetworkPolicyRuleDTO.
     * This ensures that pod-to-service edges and pod-to-pod edges targeting
     * the same backend pod are merged into a single rule.
     */
    private String buildNormalizedRuleKey(NetworkPolicyRuleDTO rule, String direction) {
        Integer effectivePort = rule.getBackendPodPort() != null ? rule.getBackendPodPort() : rule.getDestinationPort();
        if (effectivePort == null)
            effectivePort = 0;
        String protocol = rule.getProtocol() != null ? rule.getProtocol() : "TCP";

        if ("egress".equalsIgnoreCase(direction)) {
            String sourceName = rule.getSourcePodName() != null ? rule.getSourcePodName() : "_";
            String destNs = rule.getDestinationNamespace();
            if (destNs == null && rule.getServiceNamespace() != null) {
                destNs = rule.getServiceNamespace();
            }
            if (destNs == null)
                destNs = "_external";

            Map<String, String> labels = rule.getBackendPodLabels() != null ? rule.getBackendPodLabels()
                    : rule.getDestinationPodLabels();
            String destIdentity;
            if (labels != null && !labels.isEmpty()) {
                destIdentity = new TreeMap<>(selectRelevantLabels(labels)).toString();
            } else if (rule.getDestinationIp() != null) {
                destIdentity = rule.getDestinationIp();
            } else {
                destIdentity = rule.getDestinationPodName() != null ? rule.getDestinationPodName() : "_unknown";
            }

            return String.format("egress|%s|%s|%s|%d|%s", sourceName, destNs, destIdentity, effectivePort, protocol);
        } else {
            String destName = rule.getDestinationPodName() != null ? rule.getDestinationPodName() : "_";
            String srcNs = rule.getSourceNamespace() != null ? rule.getSourceNamespace() : "_external";

            Map<String, String> labels = rule.getSourcePodLabels();
            String srcIdentity;
            if (labels != null && !labels.isEmpty()) {
                srcIdentity = new TreeMap<>(selectRelevantLabels(labels)).toString();
            } else if (rule.getSourceIp() != null) {
                srcIdentity = rule.getSourceIp();
            } else {
                srcIdentity = rule.getSourcePodName() != null ? rule.getSourcePodName() : "_unknown";
            }

            return String.format("ingress|%s|%s|%s|%d|%s", destName, srcNs, srcIdentity, effectivePort, protocol);
        }
    }

    /**
     * Deduplicate NetworkPolicyPeers by their string representation.
     */
    private List<NetworkPolicyPeer> deduplicatePeers(List<NetworkPolicyPeer> peers) {
        Set<String> seen = new LinkedHashSet<>();
        List<NetworkPolicyPeer> unique = new ArrayList<>();
        for (NetworkPolicyPeer peer : peers) {
            String key = peer.toString();
            if (seen.add(key)) {
                unique.add(peer);
            }
        }
        if (unique.size() < peers.size()) {
            log.debug("Deduplicated peers: {} -> {}", peers.size(), unique.size());
        }
        return unique;
    }

    private NetworkPolicyRuleDTO buildRuleFromEdge(NetworkTopologyEdge edge, String direction) {
        NetworkPolicyRuleDTO.NetworkPolicyRuleDTOBuilder builder = NetworkPolicyRuleDTO.builder()
                .sourceNamespace(edge.getSourceNamespace())
                .sourcePodName(edge.getSourceName())
                .sourceKind(edge.getSourceType())
                .destinationNamespace(edge.getTargetNamespace())
                .destinationPodName(edge.getTargetName())
                .destinationKind(edge.getTargetType())
                .destinationPort(edge.getPort())
                .protocol(edge.getProtocol())
                .flowType(edge.getFlowType())
                .serviceName(edge.getServiceName())
                .serviceNamespace(edge.getServiceNamespace())
                .backendPodName(edge.getBackendPodName())
                .backendPodNamespace(edge.getBackendPodNamespace());

        // Fetch labels from DB
        Map<String, String> srcLabels = getPodLabels(edge.getClusterUid(), edge.getSourceNamespace(),
                edge.getSourceName());
        if (srcLabels != null) {
            builder.sourcePodLabels(srcLabels);
        }

        Map<String, String> dstLabels = getPodLabels(edge.getClusterUid(), edge.getTargetNamespace(),
                edge.getTargetName());
        if (dstLabels != null) {
            builder.destinationPodLabels(dstLabels);
        }

        if (edge.getBackendPodName() != null) {
            Map<String, String> backendLabels = getPodLabels(edge.getClusterUid(), edge.getBackendPodNamespace(),
                    edge.getBackendPodName());
            if (backendLabels != null) {
                builder.backendPodLabels(backendLabels);
            }

            // Resolve targetPort if this traffic goes through a service
            // When traffic goes through a Service, serviceName will be set
            // and we need to resolve the Service's targetPort to get the actual pod port
            if (edge.getServiceName() != null && edge.getServiceNamespace() != null) {
                log.info("Resolving backend pod port for traffic through Service: {}/{}, current port: {}",
                        edge.getServiceNamespace(), edge.getServiceName(), edge.getPort());
                Integer backendPort = resolveTargetPort(edge.getClusterUid(),
                        edge.getServiceNamespace(), edge.getServiceName(), edge.getPort());
                builder.backendPodPort(backendPort);
                log.info("Set backendPodPort to {} for service {}/{} (original port was {})",
                        backendPort, edge.getServiceNamespace(), edge.getServiceName(), edge.getPort());
            }
        }

        // Set source and destination IPs for external entities
        if ("external".equals(edge.getSourceType()) && isValidIp(edge.getSourceName())) {
            builder.sourceIp(edge.getSourceName());
        }
        if ("external".equals(edge.getTargetType()) && isValidIp(edge.getTargetName())) {
            builder.destinationIp(edge.getTargetName());
        }

        return builder.build();
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

    private NetworkPolicy buildNetworkPolicy(
            String name,
            String namespace,
            String policyType,
            Map<String, String> podSelector,
            List<NetworkPolicyRuleDTO> rules) {

        NetworkPolicyBuilder builder = new NetworkPolicyBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .addToLabels("app.kubernetes.io/managed-by", "poyraz-platform")
                .addToLabels("poyraz.io/policy-type", policyType)
                .endMetadata()
                .withNewSpec()
                .withNewPodSelector()
                .withMatchLabels(podSelector)
                .endPodSelector()
                .withPolicyTypes(policyType.substring(0, 1).toUpperCase() + policyType.substring(1))
                .endSpec();

        if ("ingress".equalsIgnoreCase(policyType)) {
            builder.editSpec()
                    .withIngress(buildIngressRules(rules))
                    .endSpec();
        } else {
            builder.editSpec()
                    .withEgress(buildEgressRules(rules))
                    .endSpec();
        }

        return builder.build();
    }

    private List<NetworkPolicyIngressRule> buildIngressRules(List<NetworkPolicyRuleDTO> rules) {
        // Group rules by port (using backendPodPort if available)
        Map<String, List<NetworkPolicyRuleDTO>> rulesByPort = rules.stream()
                .collect(Collectors.groupingBy(r -> {
                    Integer port = r.getBackendPodPort() != null ? r.getBackendPodPort() : r.getDestinationPort();
                    return r.getProtocol() + ":" + port;
                }));

        List<NetworkPolicyIngressRule> ingressRules = new ArrayList<>();

        for (Map.Entry<String, List<NetworkPolicyRuleDTO>> entry : rulesByPort.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String protocol = parts[0];
            int port = Integer.parseInt(parts[1]);

            NetworkPolicyIngressRuleBuilder ruleBuilder = new NetworkPolicyIngressRuleBuilder();

            // Add port
            ruleBuilder.addNewPort()
                    .withProtocol(protocol.toUpperCase())
                    .withNewPort(port)
                    .endPort();

            // Add peers (from)
            List<NetworkPolicyPeer> peers = new ArrayList<>();
            for (NetworkPolicyRuleDTO rule : entry.getValue()) {
                NetworkPolicyPeerBuilder peerBuilder = new NetworkPolicyPeerBuilder();

                if (rule.getSourceNamespace() != null) {
                    // Internal traffic
                    if (rule.getSourcePodLabels() != null && !rule.getSourcePodLabels().isEmpty()) {
                        peerBuilder.withNewPodSelector()
                                .withMatchLabels(selectRelevantLabels(rule.getSourcePodLabels()))
                                .endPodSelector();
                    }
                    peerBuilder.withNewNamespaceSelector()
                            .addToMatchLabels("kubernetes.io/metadata.name", rule.getSourceNamespace())
                            .endNamespaceSelector();
                    peers.add(peerBuilder.build());
                } else if (rule.getSourceIp() != null) {
                    // External traffic - use IP block
                    peerBuilder.withNewIpBlock()
                            .withCidr(rule.getSourceIp() + "/32")
                            .endIpBlock();
                    peers.add(peerBuilder.build());
                }
            }

            // Deduplicate peers
            List<NetworkPolicyPeer> uniquePeers = deduplicatePeers(peers);
            ruleBuilder.withFrom(uniquePeers);
            ingressRules.add(ruleBuilder.build());
        }

        return ingressRules;
    }

    private List<NetworkPolicyEgressRule> buildEgressRules(List<NetworkPolicyRuleDTO> rules) {
        // Group rules by port (using backendPodPort if available)
        Map<String, List<NetworkPolicyRuleDTO>> rulesByPort = rules.stream()
                .collect(Collectors.groupingBy(r -> {
                    Integer port = r.getBackendPodPort() != null ? r.getBackendPodPort() : r.getDestinationPort();
                    return r.getProtocol() + ":" + port;
                }));

        List<NetworkPolicyEgressRule> egressRules = new ArrayList<>();

        for (Map.Entry<String, List<NetworkPolicyRuleDTO>> entry : rulesByPort.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String protocol = parts[0];
            int port = Integer.parseInt(parts[1]);

            NetworkPolicyEgressRuleBuilder ruleBuilder = new NetworkPolicyEgressRuleBuilder();

            // Add port
            ruleBuilder.addNewPort()
                    .withProtocol(protocol.toUpperCase())
                    .withNewPort(port)
                    .endPort();

            // Add peers (to)
            List<NetworkPolicyPeer> peers = new ArrayList<>();
            for (NetworkPolicyRuleDTO rule : entry.getValue()) {
                NetworkPolicyPeerBuilder peerBuilder = new NetworkPolicyPeerBuilder();

                // Determine the actual destination namespace
                // For service traffic, use backend pod namespace if available
                String actualNamespace = rule.getDestinationNamespace();
                if (actualNamespace == null && rule.getServiceName() != null) {
                    // This is service traffic - use backend pod namespace
                    actualNamespace = rule.getServiceNamespace();
                }

                if (actualNamespace != null) {
                    // Internal traffic (pod-to-pod or pod-to-service)
                    Map<String, String> labels = rule.getBackendPodLabels() != null ? rule.getBackendPodLabels()
                            : rule.getDestinationPodLabels();

                    if (labels != null && !labels.isEmpty()) {
                        peerBuilder.withNewPodSelector()
                                .withMatchLabels(selectRelevantLabels(labels))
                                .endPodSelector();
                    }
                    peerBuilder.withNewNamespaceSelector()
                            .addToMatchLabels("kubernetes.io/metadata.name", actualNamespace)
                            .endNamespaceSelector();

                    peers.add(peerBuilder.build());
                } else {
                    // External traffic - use IP block
                    // For external destinations, the IP is stored in destinationPodName
                    String destinationIp = rule.getDestinationIp() != null ? rule.getDestinationIp()
                            : rule.getDestinationPodName();

                    if (destinationIp != null && isValidIp(destinationIp)) {
                        peerBuilder.withNewIpBlock()
                                .withCidr(destinationIp + "/32")
                                .endIpBlock();
                        peers.add(peerBuilder.build());
                    } else {
                        // If no valid IP, allow all external traffic for this port
                        log.warn("No valid IP for external destination, allowing all external traffic for port {}",
                                port);
                        peerBuilder.withNewIpBlock()
                                .withCidr("0.0.0.0/0")
                                .endIpBlock();
                        peers.add(peerBuilder.build());
                    }
                }
            }

            // Deduplicate peers
            List<NetworkPolicyPeer> uniquePeers = deduplicatePeers(peers);
            ruleBuilder.withTo(uniquePeers);
            egressRules.add(ruleBuilder.build());
        }

        return egressRules;
    }

    private Map<String, String> selectRelevantLabels(Map<String, String> labels) {
        // Filter out common system labels that shouldn't be used in selectors
        Set<String> excludePatterns = Set.of(
                "pod-template-hash",
                "controller-revision-hash",
                "statefulset.kubernetes.io/pod-name");

        return labels.entrySet().stream()
                .filter(e -> excludePatterns.stream().noneMatch(p -> e.getKey().contains(p)))
                .limit(3) // Limit to most relevant labels
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // Simple IP validation - check if it matches IPv4 pattern
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

    private String generatePolicyName(String namespace, String policyType, Map<String, String> podSelector) {
        String selectorPart = podSelector != null && !podSelector.isEmpty()
                ? podSelector.values().iterator().next().toLowerCase().replaceAll("[^a-z0-9]", "-")
                : "all";
        String timestamp = String.valueOf(System.currentTimeMillis() % 10000);
        return String.format("generated-%s-%s-%s", policyType, selectorPart, timestamp);
    }

    private String convertToYaml(NetworkPolicy policy) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);

        Map<String, Object> policyMap = new LinkedHashMap<>();
        policyMap.put("apiVersion", "networking.k8s.io/v1");
        policyMap.put("kind", "NetworkPolicy");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", policy.getMetadata().getName());
        metadata.put("namespace", policy.getMetadata().getNamespace());
        if (policy.getMetadata().getLabels() != null) {
            metadata.put("labels", policy.getMetadata().getLabels());
        }
        policyMap.put("metadata", metadata);

        Map<String, Object> spec = new LinkedHashMap<>();

        // Pod selector
        Map<String, Object> podSelector = new LinkedHashMap<>();
        if (policy.getSpec().getPodSelector().getMatchLabels() != null) {
            podSelector.put("matchLabels", policy.getSpec().getPodSelector().getMatchLabels());
        }
        spec.put("podSelector", podSelector);

        // Policy types
        spec.put("policyTypes", policy.getSpec().getPolicyTypes());

        // Ingress rules
        if (policy.getSpec().getIngress() != null && !policy.getSpec().getIngress().isEmpty()) {
            spec.put("ingress", convertIngressRulesToMap(policy.getSpec().getIngress()));
        }

        // Egress rules
        if (policy.getSpec().getEgress() != null && !policy.getSpec().getEgress().isEmpty()) {
            spec.put("egress", convertEgressRulesToMap(policy.getSpec().getEgress()));
        }

        policyMap.put("spec", spec);

        return yaml.dump(policyMap);
    }

    private List<Map<String, Object>> convertIngressRulesToMap(List<NetworkPolicyIngressRule> rules) {
        return rules.stream().map(rule -> {
            Map<String, Object> ruleMap = new LinkedHashMap<>();

            if (rule.getFrom() != null && !rule.getFrom().isEmpty()) {
                ruleMap.put("from", rule.getFrom().stream().map(this::convertPeerToMap).collect(Collectors.toList()));
            }

            if (rule.getPorts() != null && !rule.getPorts().isEmpty()) {
                ruleMap.put("ports", rule.getPorts().stream().map(this::convertPortToMap).collect(Collectors.toList()));
            }

            return ruleMap;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> convertEgressRulesToMap(List<NetworkPolicyEgressRule> rules) {
        return rules.stream().map(rule -> {
            Map<String, Object> ruleMap = new LinkedHashMap<>();

            if (rule.getTo() != null && !rule.getTo().isEmpty()) {
                ruleMap.put("to", rule.getTo().stream().map(this::convertPeerToMap).collect(Collectors.toList()));
            }

            if (rule.getPorts() != null && !rule.getPorts().isEmpty()) {
                ruleMap.put("ports", rule.getPorts().stream().map(this::convertPortToMap).collect(Collectors.toList()));
            }

            return ruleMap;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> convertPeerToMap(NetworkPolicyPeer peer) {
        Map<String, Object> peerMap = new LinkedHashMap<>();

        if (peer.getPodSelector() != null && peer.getPodSelector().getMatchLabels() != null) {
            Map<String, Object> podSelector = new LinkedHashMap<>();
            podSelector.put("matchLabels", peer.getPodSelector().getMatchLabels());
            peerMap.put("podSelector", podSelector);
        }

        if (peer.getNamespaceSelector() != null && peer.getNamespaceSelector().getMatchLabels() != null) {
            Map<String, Object> nsSelector = new LinkedHashMap<>();
            nsSelector.put("matchLabels", peer.getNamespaceSelector().getMatchLabels());
            peerMap.put("namespaceSelector", nsSelector);
        }

        if (peer.getIpBlock() != null) {
            Map<String, Object> ipBlock = new LinkedHashMap<>();
            ipBlock.put("cidr", peer.getIpBlock().getCidr());
            if (peer.getIpBlock().getExcept() != null) {
                ipBlock.put("except", peer.getIpBlock().getExcept());
            }
            peerMap.put("ipBlock", ipBlock);
        }

        return peerMap;
    }

    private Map<String, Object> convertPortToMap(NetworkPolicyPort port) {
        Map<String, Object> portMap = new LinkedHashMap<>();
        portMap.put("protocol", port.getProtocol());
        portMap.put("port", port.getPort().getIntVal());
        return portMap;
    }

    private String serializeMap(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map != null ? map : Collections.emptyMap());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String serializeRules(List<NetworkPolicyRuleDTO> rules) {
        try {
            return objectMapper.writeValueAsString(rules != null ? rules : Collections.emptyList());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private GeneratedNetworkPolicyDTO convertToDTO(GeneratedNetworkPolicy entity) {
        GeneratedNetworkPolicyDTO.GeneratedNetworkPolicyDTOBuilder builder = GeneratedNetworkPolicyDTO.builder()
                .id(entity.getId())
                .clusterUid(entity.getClusterUid())
                .namespace(entity.getNamespace())
                .name(entity.getName())
                .policyType(entity.getPolicyType())
                .yamlContent(entity.getYamlContent())
                .status(entity.getStatus())
                .description(entity.getDescription())
                .createdBy(entity.getCreatedBy())
                .appliedAt(entity.getAppliedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        // Parse pod selector
        try {
            Map<String, String> podSelector = objectMapper.readValue(entity.getPodSelector(),
                    new TypeReference<Map<String, String>>() {
                    });
            builder.podSelector(podSelector);
        } catch (JsonProcessingException e) {
            builder.podSelector(Collections.emptyMap());
        }

        // Parse rules
        try {
            List<NetworkPolicyRuleDTO> rules = objectMapper.readValue(entity.getRules(),
                    new TypeReference<List<NetworkPolicyRuleDTO>>() {
                    });
            builder.rules(rules);
        } catch (JsonProcessingException e) {
            builder.rules(Collections.emptyList());
        }

        // Get migration info
        Optional<Integer> maxVersion = migrationService.getMaxVersion(entity.getId());
        builder.currentVersion(maxVersion.orElse(1));
        builder.totalVersions((int) migrationService.getMigrationCount(entity.getId()));

        return builder.build();
    }
}
