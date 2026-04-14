package com.k8s.platform.service.network;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.network.GeneratedNetworkPolicy;
import com.k8s.platform.domain.repository.network.GeneratedNetworkPolicyRepository;
import com.k8s.platform.dto.network.GeneratedNetworkPolicyDTO;
import com.k8s.platform.dto.network.PolicyConflictDTO;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyEgressRule;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyPeer;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyConflictService {

    private final GeneratedNetworkPolicyRepository generatedPolicyRepository;
    private final ClusterContextManager clusterContextManager;
    private final ObjectMapper objectMapper;

    /**
     * Check for conflicts between a new policy and existing policies
     */
    public List<PolicyConflictDTO> checkForConflicts(
            String clusterUid, 
            String namespace,
            GeneratedNetworkPolicyDTO newPolicy) {
        
        log.info("Checking for conflicts for policy: {} in namespace: {}", 
                newPolicy.getName(), namespace);

        List<PolicyConflictDTO> conflicts = new ArrayList<>();

        // Check against existing policies in the database (generated policies)
        List<GeneratedNetworkPolicy> existingGenerated = generatedPolicyRepository
                .findActiveByClusterAndNamespaceAndType(clusterUid, namespace, newPolicy.getPolicyType());
        
        for (GeneratedNetworkPolicy existing : existingGenerated) {
            if (newPolicy.getId() != null && newPolicy.getId().equals(existing.getId())) {
                continue; // Skip self
            }
            
            List<PolicyConflictDTO> dbConflicts = checkConflictsBetweenPolicies(existing, newPolicy);
            conflicts.addAll(dbConflicts);
        }

        // Check against existing policies in the Kubernetes cluster
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterUid);
            List<NetworkPolicy> clusterPolicies = client.network().v1().networkPolicies()
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            for (NetworkPolicy clusterPolicy : clusterPolicies) {
                // Skip if this is the same policy we're creating/updating
                if (newPolicy.getName() != null && 
                        newPolicy.getName().equals(clusterPolicy.getMetadata().getName())) {
                    continue;
                }

                List<PolicyConflictDTO> clusterConflicts = checkConflictsWithClusterPolicy(
                        clusterPolicy, newPolicy);
                conflicts.addAll(clusterConflicts);
            }
        } catch (Exception e) {
            log.warn("Failed to check cluster policies for conflicts: {}", e.getMessage());
        }

        log.info("Found {} potential conflicts", conflicts.size());
        return conflicts;
    }

    private List<PolicyConflictDTO> checkConflictsBetweenPolicies(
            GeneratedNetworkPolicy existing, 
            GeneratedNetworkPolicyDTO newPolicy) {
        
        List<PolicyConflictDTO> conflicts = new ArrayList<>();

        // Parse existing policy's pod selector
        Map<String, String> existingSelector;
        try {
            existingSelector = objectMapper.readValue(existing.getPodSelector(), 
                    new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            existingSelector = Collections.emptyMap();
        }

        // Check for duplicate/overlapping pod selectors
        if (selectorsOverlap(existingSelector, newPolicy.getPodSelector())) {
            PolicyConflictDTO conflict = PolicyConflictDTO.builder()
                    .existingPolicyName(existing.getName())
                    .existingPolicyNamespace(existing.getNamespace())
                    .existingPolicyType(existing.getPolicyType())
                    .conflictType(PolicyConflictDTO.ConflictType.DUPLICATE_SELECTOR)
                    .severity("warning")
                    .description(String.format(
                            "Policy '%s' has overlapping pod selector with existing policy '%s'. " +
                            "Both policies may apply to the same pods.",
                            newPolicy.getName(), existing.getName()))
                    .details(List.of(
                            PolicyConflictDTO.ConflictDetail.builder()
                                    .field("podSelector")
                                    .existingValue(existingSelector.toString())
                                    .newValue(newPolicy.getPodSelector() != null ? 
                                            newPolicy.getPodSelector().toString() : "{}")
                                    .explanation("Pod selectors match or overlap")
                                    .build()
                    ))
                    .autoResolvable(false)
                    .suggestedResolution("Consider merging the policies or using more specific pod selectors")
                    .build();
            
            conflicts.add(conflict);
        }

        return conflicts;
    }

    private List<PolicyConflictDTO> checkConflictsWithClusterPolicy(
            NetworkPolicy clusterPolicy, 
            GeneratedNetworkPolicyDTO newPolicy) {
        
        List<PolicyConflictDTO> conflicts = new ArrayList<>();
        
        // Check policy type match
        List<String> clusterPolicyTypes = clusterPolicy.getSpec().getPolicyTypes();
        String newPolicyType = newPolicy.getPolicyType().substring(0, 1).toUpperCase() + 
                newPolicy.getPolicyType().substring(1);
        
        if (clusterPolicyTypes == null || !clusterPolicyTypes.contains(newPolicyType)) {
            return conflicts; // Different policy types, no conflict
        }

        // Check pod selector overlap
        Map<String, String> clusterSelector = clusterPolicy.getSpec().getPodSelector() != null ?
                clusterPolicy.getSpec().getPodSelector().getMatchLabels() : Collections.emptyMap();
        
        if (selectorsOverlap(clusterSelector, newPolicy.getPodSelector())) {
            // Check for rule conflicts
            if ("ingress".equalsIgnoreCase(newPolicy.getPolicyType())) {
                List<PolicyConflictDTO> ingressConflicts = checkIngressRuleConflicts(
                        clusterPolicy, newPolicy);
                conflicts.addAll(ingressConflicts);
            } else {
                List<PolicyConflictDTO> egressConflicts = checkEgressRuleConflicts(
                        clusterPolicy, newPolicy);
                conflicts.addAll(egressConflicts);
            }
            
            // If same selector but no specific rule conflicts, warn about overlap
            if (conflicts.isEmpty()) {
                conflicts.add(PolicyConflictDTO.builder()
                        .existingPolicyName(clusterPolicy.getMetadata().getName())
                        .existingPolicyNamespace(clusterPolicy.getMetadata().getNamespace())
                        .existingPolicyType(newPolicy.getPolicyType())
                        .conflictType(PolicyConflictDTO.ConflictType.OVERLAPPING_RULES)
                        .severity("info")
                        .description(String.format(
                                "Existing cluster policy '%s' targets the same pods. " +
                                "Network policies are additive, so both will apply.",
                                clusterPolicy.getMetadata().getName()))
                        .autoResolvable(true)
                        .suggestedResolution("This is normal behavior - network policies are additive")
                        .build());
            }
        }

        return conflicts;
    }

    private List<PolicyConflictDTO> checkIngressRuleConflicts(
            NetworkPolicy clusterPolicy, 
            GeneratedNetworkPolicyDTO newPolicy) {
        
        List<PolicyConflictDTO> conflicts = new ArrayList<>();
        List<NetworkPolicyIngressRule> clusterIngress = clusterPolicy.getSpec().getIngress();
        
        if (clusterIngress == null || clusterIngress.isEmpty()) {
            // Existing policy has no ingress rules - might be a default deny
            if (clusterPolicy.getSpec().getPolicyTypes() != null && 
                    clusterPolicy.getSpec().getPolicyTypes().contains("Ingress")) {
                conflicts.add(PolicyConflictDTO.builder()
                        .existingPolicyName(clusterPolicy.getMetadata().getName())
                        .existingPolicyNamespace(clusterPolicy.getMetadata().getNamespace())
                        .existingPolicyType("ingress")
                        .conflictType(PolicyConflictDTO.ConflictType.DEFAULT_DENY_OVERRIDE)
                        .severity("warning")
                        .description(String.format(
                                "Existing policy '%s' appears to be a default deny policy. " +
                                "Your new policy will allow additional traffic.",
                                clusterPolicy.getMetadata().getName()))
                        .autoResolvable(true)
                        .suggestedResolution("This may be intentional - new policy adds allowed traffic")
                        .build());
            }
        }

        return conflicts;
    }

    private List<PolicyConflictDTO> checkEgressRuleConflicts(
            NetworkPolicy clusterPolicy, 
            GeneratedNetworkPolicyDTO newPolicy) {
        
        List<PolicyConflictDTO> conflicts = new ArrayList<>();
        List<NetworkPolicyEgressRule> clusterEgress = clusterPolicy.getSpec().getEgress();
        
        if (clusterEgress == null || clusterEgress.isEmpty()) {
            // Existing policy has no egress rules - might be a default deny
            if (clusterPolicy.getSpec().getPolicyTypes() != null && 
                    clusterPolicy.getSpec().getPolicyTypes().contains("Egress")) {
                conflicts.add(PolicyConflictDTO.builder()
                        .existingPolicyName(clusterPolicy.getMetadata().getName())
                        .existingPolicyNamespace(clusterPolicy.getMetadata().getNamespace())
                        .existingPolicyType("egress")
                        .conflictType(PolicyConflictDTO.ConflictType.DEFAULT_DENY_OVERRIDE)
                        .severity("warning")
                        .description(String.format(
                                "Existing policy '%s' appears to be a default deny policy. " +
                                "Your new policy will allow additional traffic.",
                                clusterPolicy.getMetadata().getName()))
                        .autoResolvable(true)
                        .suggestedResolution("This may be intentional - new policy adds allowed traffic")
                        .build());
            }
        }

        return conflicts;
    }

    private boolean selectorsOverlap(Map<String, String> selector1, Map<String, String> selector2) {
        final Map<String, String> sel1 = selector1 != null ? selector1 : Collections.emptyMap();
        final Map<String, String> sel2 = selector2 != null ? selector2 : Collections.emptyMap();
        
        // Empty selector matches all pods
        if (sel1.isEmpty() || sel2.isEmpty()) {
            return true;
        }
        
        // Check if all labels in selector1 are present and match in selector2
        boolean selector1SubsetOf2 = sel1.entrySet().stream()
                .allMatch(e -> e.getValue().equals(sel2.get(e.getKey())));
        
        // Check if all labels in selector2 are present and match in selector1
        boolean selector2SubsetOf1 = sel2.entrySet().stream()
                .allMatch(e -> e.getValue().equals(sel1.get(e.getKey())));
        
        // Selectors overlap if one is a subset of the other (or they're equal)
        return selector1SubsetOf2 || selector2SubsetOf1;
    }
}
