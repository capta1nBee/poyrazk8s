package com.k8s.platform.service.network;

import com.k8s.platform.domain.entity.network.GeneratedNetworkPolicy;
import com.k8s.platform.domain.entity.network.NetworkPolicyMigration;
import com.k8s.platform.domain.repository.network.GeneratedNetworkPolicyRepository;
import com.k8s.platform.domain.repository.network.NetworkPolicyMigrationRepository;
import com.k8s.platform.dto.network.PolicyMigrationDTO;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyMigrationService {

    private final NetworkPolicyMigrationRepository migrationRepository;
    private final GeneratedNetworkPolicyRepository policyRepository;
    private final ClusterContextManager clusterContextManager;

    /**
     * Record a migration/version change for a policy
     */
    @Transactional
    public NetworkPolicyMigration recordMigration(
            GeneratedNetworkPolicy policy,
            String action,
            String previousYaml,
            String newYaml,
            String description,
            String username) {
        
        // Get next version number
        Integer maxVersion = migrationRepository.findMaxVersionByPolicyId(policy.getId())
                .orElse(0);
        int nextVersion = maxVersion + 1;

        NetworkPolicyMigration migration = NetworkPolicyMigration.builder()
                .policyId(policy.getId())
                .version(nextVersion)
                .action(action)
                .previousYaml(previousYaml)
                .newYaml(newYaml)
                .changeDescription(description)
                .appliedBy(username)
                .appliedAt(LocalDateTime.now())
                .build();

        migration = migrationRepository.save(migration);
        
        log.info("Recorded migration v{} for policy {}: {}", nextVersion, policy.getName(), action);
        return migration;
    }

    /**
     * Get migration history for a policy
     */
    public List<PolicyMigrationDTO> getMigrationHistory(Long policyId) {
        GeneratedNetworkPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));
        
        List<NetworkPolicyMigration> migrations = migrationRepository
                .findByPolicyIdOrderByVersionDesc(policyId);
        
        return migrations.stream()
                .map(m -> convertToDTO(m, policy))
                .collect(Collectors.toList());
    }

    /**
     * Get a specific migration by ID
     */
    public PolicyMigrationDTO getMigration(Long migrationId) {
        NetworkPolicyMigration migration = migrationRepository.findById(migrationId)
                .orElseThrow(() -> new RuntimeException("Migration not found: " + migrationId));
        
        GeneratedNetworkPolicy policy = policyRepository.findById(migration.getPolicyId())
                .orElseThrow(() -> new RuntimeException("Policy not found: " + migration.getPolicyId()));
        
        return convertToDTO(migration, policy);
    }

    /**
     * Rollback a policy to a specific version
     */
    @Transactional
    public PolicyMigrationDTO rollbackToVersion(
            String clusterUid,
            Long policyId, 
            Integer targetVersion,
            String username) {
        
        GeneratedNetworkPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        if (!policy.getClusterUid().equals(clusterUid)) {
            throw new RuntimeException("Policy does not belong to this cluster");
        }

        // Find the target version
        NetworkPolicyMigration targetMigration = migrationRepository
                .findByPolicyIdAndVersion(policyId, targetVersion)
                .orElseThrow(() -> new RuntimeException("Version " + targetVersion + " not found"));

        if (targetMigration.getNewYaml() == null) {
            throw new RuntimeException("Cannot rollback to a delete action");
        }

        String currentYaml = policy.getYamlContent();
        String targetYaml = targetMigration.getNewYaml();

        // Update the policy with the target version's YAML
        policy.setYamlContent(targetYaml);
        policy.setStatus("draft"); // Mark as draft since it needs to be re-applied
        policyRepository.save(policy);

        // Mark intermediate migrations as rolled back
        List<NetworkPolicyMigration> laterMigrations = migrationRepository
                .findByPolicyIdOrderByVersionDesc(policyId)
                .stream()
                .filter(m -> m.getVersion() > targetVersion && m.getRollbackAt() == null)
                .collect(Collectors.toList());

        for (NetworkPolicyMigration m : laterMigrations) {
            m.setRollbackAt(LocalDateTime.now());
            m.setRolledBackBy(username);
            migrationRepository.save(m);
        }

        // Record the rollback as a new migration
        NetworkPolicyMigration rollbackMigration = recordMigration(
                policy,
                "rollback",
                currentYaml,
                targetYaml,
                "Rolled back to version " + targetVersion,
                username);

        log.info("Rolled back policy {} to version {}", policy.getName(), targetVersion);
        return convertToDTO(rollbackMigration, policy);
    }

    /**
     * Rollback and apply to cluster
     */
    @Transactional
    public PolicyMigrationDTO rollbackAndApply(
            String clusterUid,
            Long policyId,
            Integer targetVersion,
            String username) {
        
        // First rollback in database
        PolicyMigrationDTO rollbackResult = rollbackToVersion(clusterUid, policyId, targetVersion, username);
        
        GeneratedNetworkPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new RuntimeException("Policy not found: " + policyId));

        // Then apply to cluster
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterUid);
            NetworkPolicy k8sPolicy = client.network().v1().networkPolicies()
                    .load(new java.io.ByteArrayInputStream(policy.getYamlContent().getBytes()))
                    .item();

            client.network().v1().networkPolicies()
                    .inNamespace(policy.getNamespace())
                    .withName(policy.getName())
                    .replace(k8sPolicy);

            // Update status
            policy.setStatus("applied");
            policy.setAppliedAt(LocalDateTime.now());
            policyRepository.save(policy);

            log.info("Applied rolled back policy {} to cluster", policy.getName());
        } catch (Exception e) {
            log.error("Failed to apply rolled back policy to cluster: {}", e.getMessage(), e);
            throw new RuntimeException("Rollback succeeded but failed to apply to cluster: " + e.getMessage());
        }

        return rollbackResult;
    }

    /**
     * Get the maximum version number for a policy
     */
    public Optional<Integer> getMaxVersion(Long policyId) {
        return migrationRepository.findMaxVersionByPolicyId(policyId);
    }

    /**
     * Get the count of migrations for a policy
     */
    public long getMigrationCount(Long policyId) {
        return migrationRepository.countByPolicyId(policyId);
    }

    /**
     * Get diff summary between two versions
     */
    public String getDiffSummary(String previousYaml, String newYaml) {
        if (previousYaml == null && newYaml == null) {
            return "No changes";
        }
        if (previousYaml == null) {
            return "Initial creation";
        }
        if (newYaml == null) {
            return "Policy deleted";
        }
        
        // Simple line-based diff summary
        String[] prevLines = previousYaml.split("\n");
        String[] newLines = newYaml.split("\n");
        
        int added = 0;
        int removed = 0;
        
        // Very simple diff - just count line differences
        java.util.Set<String> prevSet = new java.util.HashSet<>(java.util.Arrays.asList(prevLines));
        java.util.Set<String> newSet = new java.util.HashSet<>(java.util.Arrays.asList(newLines));
        
        for (String line : newLines) {
            if (!prevSet.contains(line)) {
                added++;
            }
        }
        
        for (String line : prevLines) {
            if (!newSet.contains(line)) {
                removed++;
            }
        }
        
        if (added == 0 && removed == 0) {
            return "No significant changes";
        }
        
        return String.format("+%d lines, -%d lines", added, removed);
    }

    private PolicyMigrationDTO convertToDTO(NetworkPolicyMigration migration, GeneratedNetworkPolicy policy) {
        PolicyMigrationDTO dto = PolicyMigrationDTO.builder()
                .id(migration.getId())
                .policyId(migration.getPolicyId())
                .policyName(policy.getName())
                .policyNamespace(policy.getNamespace())
                .version(migration.getVersion())
                .action(migration.getAction())
                .previousYaml(migration.getPreviousYaml())
                .newYaml(migration.getNewYaml())
                .changeDescription(migration.getChangeDescription())
                .appliedBy(migration.getAppliedBy())
                .appliedAt(migration.getAppliedAt())
                .rollbackAt(migration.getRollbackAt())
                .rolledBackBy(migration.getRolledBackBy())
                .createdAt(migration.getCreatedAt())
                .canRollback(migration.getRollbackAt() == null && migration.getNewYaml() != null)
                .diffSummary(getDiffSummary(migration.getPreviousYaml(), migration.getNewYaml()))
                .build();
        
        return dto;
    }
}
