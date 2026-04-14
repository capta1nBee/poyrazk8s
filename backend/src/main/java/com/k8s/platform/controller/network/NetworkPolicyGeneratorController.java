package com.k8s.platform.controller.network;

import com.k8s.platform.dto.network.*;
import com.k8s.platform.dto.response.ApiResponse;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.k8s.NamespaceService;
import com.k8s.platform.service.network.NetworkPolicyGeneratorService;
import com.k8s.platform.service.network.PolicyConflictService;
import com.k8s.platform.service.network.PolicyMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/{clusterUid}/network-policy-generator")
@RequiredArgsConstructor
@Slf4j
public class NetworkPolicyGeneratorController {

    private final NetworkPolicyGeneratorService generatorService;
    private final NamespaceService namespaceService;
    private final PolicyConflictService conflictService;
    private final PolicyMigrationService migrationService;
    private final ResourceAuthorizationHelper authHelper;

    /**
     * Extract distinct traffic rules from network flows
     */
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<NetworkPolicyRuleDTO>>> extractRules(
            @PathVariable String clusterUid,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String labelKey,
            @RequestParam(required = false) String labelValue,
            @RequestParam(defaultValue = "ingress") String direction) {

        log.info("Extracting rules for cluster: {}, namespace: {}, label: {}={}, direction: {}",
                clusterUid, namespace, labelKey, labelValue, direction);

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        List<NetworkPolicyRuleDTO> rules = generatorService.extractDistinctRulesByLabel(
                clusterUid, namespace, labelKey, labelValue, direction);

        return ResponseEntity.ok(ApiResponse.success("Rules extracted successfully", rules));
    }

    /**
     * Generate a network policy from selected rules
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<GeneratedNetworkPolicyDTO>> generatePolicy(
            @PathVariable String clusterUid,
            @RequestBody GeneratePolicyRequestDTO request) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        String username = getCurrentUsername();
        log.info("Generating {} policy for namespace: {} by user: {}",
                request.getPolicyType(), request.getNamespace(), username);

        GeneratedNetworkPolicyDTO policy = generatorService.generatePolicy(clusterUid, request, username);

        return ResponseEntity.ok(ApiResponse.success("Policy generated successfully", policy));
    }

    /**
     * Get all generated policies for a cluster
     */
    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<GeneratedNetworkPolicyDTO>>> getPolicies(
            @PathVariable String clusterUid,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String status) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        List<GeneratedNetworkPolicyDTO> policies = generatorService.getPolicies(clusterUid, namespace, status);

        return ResponseEntity.ok(ApiResponse.success("Policies retrieved successfully", policies));
    }

    /**
     * Get a single generated policy by ID
     */
    @GetMapping("/policies/{policyId}")
    public ResponseEntity<ApiResponse<GeneratedNetworkPolicyDTO>> getPolicy(
            @PathVariable String clusterUid,
            @PathVariable Long policyId) {

        GeneratedNetworkPolicyDTO policy = generatorService.getPolicy(clusterUid, policyId);
        if (policy == null)
            return ResponseEntity.notFound().build();

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        return ResponseEntity.ok(ApiResponse.success("Policy retrieved successfully", policy));
    }

    /**
     * Update a policy's YAML content
     */
    @PutMapping("/policies/{policyId}")
    public ResponseEntity<ApiResponse<GeneratedNetworkPolicyDTO>> updatePolicy(
            @PathVariable String clusterUid,
            @PathVariable Long policyId,
            @RequestBody Map<String, String> request) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        GeneratedNetworkPolicyDTO existingPolicy = generatorService.getPolicy(clusterUid, policyId);
        if (existingPolicy == null)
            return ResponseEntity.notFound().build();

        String username = getCurrentUsername();
        String yamlContent = request.get("yamlContent");
        String description = request.get("description");

        GeneratedNetworkPolicyDTO policy = generatorService.updatePolicy(
                clusterUid, policyId, yamlContent, description, username);

        return ResponseEntity.ok(ApiResponse.success("Policy updated successfully", policy));
    }

    /**
     * Check for conflicts before applying a policy
     */
    @PostMapping("/check-conflicts")
    public ResponseEntity<ApiResponse<List<PolicyConflictDTO>>> checkConflicts(
            @PathVariable String clusterUid,
            @RequestBody GeneratedNetworkPolicyDTO policy) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        List<PolicyConflictDTO> conflicts = conflictService.checkForConflicts(
                clusterUid, policy.getNamespace(), policy);

        return ResponseEntity.ok(ApiResponse.success("Conflicts checked successfully", conflicts));
    }

    /**
     * Apply a generated policy to the Kubernetes cluster
     */
    @PostMapping("/policies/{policyId}/apply")
    public ResponseEntity<ApiResponse<GeneratedNetworkPolicyDTO>> applyPolicy(
            @PathVariable String clusterUid,
            @PathVariable Long policyId) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        GeneratedNetworkPolicyDTO existingPolicy = generatorService.getPolicy(clusterUid, policyId);
        if (existingPolicy == null)
            return ResponseEntity.notFound().build();

        String username = getCurrentUsername();
        log.info("Applying policy {} to cluster {} by user: {}", policyId, clusterUid, username);

        GeneratedNetworkPolicyDTO policy = generatorService.applyPolicy(clusterUid, policyId, username);

        return ResponseEntity.ok(ApiResponse.success("Policy applied successfully", policy));
    }

    /**
     * Delete a network policy
     */
    @DeleteMapping("/policies/{policyId}")
    public ResponseEntity<ApiResponse<String>> deletePolicy(
            @PathVariable String clusterUid,
            @PathVariable Long policyId,
            @RequestParam(defaultValue = "true") boolean deleteFromCluster) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        GeneratedNetworkPolicyDTO existingPolicy = generatorService.getPolicy(clusterUid, policyId);
        if (existingPolicy == null)
            return ResponseEntity.notFound().build();

        String username = getCurrentUsername();
        log.info("Deleting policy {} from cluster {} by user: {}, deleteFromCluster: {}",
                policyId, clusterUid, username, deleteFromCluster);

        generatorService.deletePolicy(clusterUid, policyId, deleteFromCluster, username);

        return ResponseEntity.ok(ApiResponse.success("Policy deleted successfully"));
    }

    /**
     * Get migration history for a policy
     */
    @GetMapping("/policies/{policyId}/migrations")
    public ResponseEntity<ApiResponse<List<PolicyMigrationDTO>>> getMigrationHistory(
            @PathVariable String clusterUid,
            @PathVariable Long policyId) {

        GeneratedNetworkPolicyDTO policy = generatorService.getPolicy(clusterUid, policyId);
        if (policy == null)
            return ResponseEntity.notFound().build();

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        List<PolicyMigrationDTO> history = migrationService.getMigrationHistory(policyId);

        return ResponseEntity.ok(ApiResponse.success("Migration history retrieved successfully", history));
    }

    /**
     * Get a specific migration
     */
    @GetMapping("/migrations/{migrationId}")
    public ResponseEntity<ApiResponse<PolicyMigrationDTO>> getMigration(
            @PathVariable String clusterUid,
            @PathVariable Long migrationId) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        PolicyMigrationDTO migration = migrationService.getMigration(migrationId);

        return ResponseEntity.ok(ApiResponse.success("Migration retrieved successfully", migration));
    }

    /**
     * Rollback a policy to a specific version
     */
    @PostMapping("/policies/{policyId}/rollback")
    public ResponseEntity<ApiResponse<PolicyMigrationDTO>> rollbackPolicy(
            @PathVariable String clusterUid,
            @PathVariable Long policyId,
            @RequestParam Integer targetVersion,
            @RequestParam(defaultValue = "false") boolean applyToCluster) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        GeneratedNetworkPolicyDTO existingPolicy = generatorService.getPolicy(clusterUid, policyId);
        if (existingPolicy == null)
            return ResponseEntity.notFound().build();

        String username = getCurrentUsername();
        log.info("Rolling back policy {} to version {} by user: {}, applyToCluster: {}",
                policyId, targetVersion, username, applyToCluster);

        PolicyMigrationDTO result;
        if (applyToCluster) {
            result = migrationService.rollbackAndApply(clusterUid, policyId, targetVersion, username);
        } else {
            result = migrationService.rollbackToVersion(clusterUid, policyId, targetVersion, username);
        }

        return ResponseEntity.ok(ApiResponse.success("Policy rolled back successfully", result));
    }

    /**
     * Get all namespaces for policy generator (T3 — unfiltered, all namespaces)
     */
    @GetMapping("/namespaces")
    public ResponseEntity<ApiResponse<List<String>>> getNamespacesWithFlows(
            @PathVariable String clusterUid) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        // T3: return ALL namespaces unfiltered
        List<String> namespaces = namespaceService.listNamespaces(clusterUid, false).stream()
                .map(ns -> ns.getName())
                .sorted()
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Namespaces retrieved successfully", namespaces));
    }

    /**
     * Get policy labels for a namespace (workload selector based on configured
     * policy labels)
     */
    @GetMapping("/labels")
    public ResponseEntity<ApiResponse<List<PolicyLabelDTO>>> getWorkloadLabels(
            @PathVariable String clusterUid,
            @RequestParam String namespace,
            @RequestParam(defaultValue = "ingress") String direction) {

        log.info("Getting workload labels for cluster: {}, namespace: {}, direction: {}",
                clusterUid, namespace, direction);

        authHelper.checkPagePermissionOrThrow(clusterUid, "network-policy-gen");

        List<PolicyLabelDTO> labels = generatorService.getWorkloadLabels(clusterUid, namespace, direction);

        return ResponseEntity.ok(ApiResponse.success("Labels retrieved successfully", labels));
    }

    /**
     * Get configured policy label keys
     */
    @GetMapping("/label-keys")
    public ResponseEntity<ApiResponse<List<String>>> getPolicyLabelKeys() {
        // Global config, should probably be viewable by anyone who can access policy
        // generator
        List<String> labelKeys = generatorService.getPolicyLabelKeys();
        return ResponseEntity.ok(ApiResponse.success("Label keys retrieved successfully", labelKeys));
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return "system";
    }
}
