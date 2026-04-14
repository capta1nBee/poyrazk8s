package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.k8s.Pod;
import com.k8s.platform.domain.repository.k8s.PodRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentActionsService {

        private final ClusterContextManager clusterContextManager;
        private final PodRepository podRepository;

        /**
         * Scale deployment replicas
         */
        public Map<String, Object> scaleReplicas(String clusterUid, String namespace,
                        String name, Integer replicas) {
                log.info("Scaling deployment {}/{} to {} replicas in cluster {}",
                                namespace, name, replicas, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .scale(replicas);

                return Map.of(
                                "message", "Deployment scaled successfully",
                                "deployment", name,
                                "namespace", namespace,
                                "replicas", replicas);
        }

        /**
         * Rollout restart deployment
         */
        public Map<String, String> rolloutRestart(String clusterUid, String namespace, String name) {
                log.info("Rollout restart for deployment {}/{} in cluster {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Add/update restart annotation to trigger rollout
                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .edit(deployment -> {
                                        if (deployment.getSpec().getTemplate().getMetadata().getAnnotations() == null) {
                                                deployment.getSpec().getTemplate().getMetadata()
                                                                .setAnnotations(new HashMap<>());
                                        }
                                        deployment.getSpec().getTemplate().getMetadata().getAnnotations()
                                                        .put("kubectl.kubernetes.io/restartedAt",
                                                                        Instant.now().toString());
                                        return deployment;
                                });

                return Map.of(
                                "message", "Deployment rollout restart initiated",
                                "deployment", name,
                                "namespace", namespace);
        }

        /**
         * Pause deployment rollout
         */
        public Map<String, String> pauseRollout(String clusterUid, String namespace, String name) {
                log.info("Pausing rollout for deployment {}/{} in cluster {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .edit(deployment -> {
                                        deployment.getSpec().setPaused(true);
                                        return deployment;
                                });

                return Map.of(
                                "message", "Deployment rollout paused",
                                "deployment", name,
                                "namespace", namespace);
        }

        /**
         * Resume deployment rollout
         */
        public Map<String, String> resumeRollout(String clusterUid, String namespace, String name) {
                log.info("Resuming rollout for deployment {}/{} in cluster {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .edit(deployment -> {
                                        deployment.getSpec().setPaused(false);
                                        return deployment;
                                });

                return Map.of(
                                "message", "Deployment rollout resumed",
                                "deployment", name,
                                "namespace", namespace);
        }

        /**
         * View rollout history
         */
        public List<Map<String, Object>> viewRolloutHistory(String clusterUid, String namespace, String name) {
                log.info("Getting rollout history for deployment {}/{} in cluster {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Get all ReplicaSets owned by this deployment
                List<ReplicaSet> replicaSets = client.apps().replicaSets()
                                .inNamespace(namespace)
                                .list()
                                .getItems()
                                .stream()
                                .filter(rs -> rs.getMetadata().getOwnerReferences() != null &&
                                                rs.getMetadata().getOwnerReferences().stream()
                                                                .anyMatch(ref -> ref.getName().equals(name)))
                                .collect(Collectors.toList());

                return replicaSets.stream()
                                .map(rs -> {
                                        Map<String, Object> revision = new HashMap<>();
                                        revision.put("name", rs.getMetadata().getName());
                                        revision.put("revision", rs.getMetadata().getAnnotations() != null
                                                        ? rs.getMetadata().getAnnotations()
                                                                        .get("deployment.kubernetes.io/revision")
                                                        : "unknown");
                                        revision.put("changeCause", rs.getMetadata().getAnnotations() != null
                                                        ? rs.getMetadata().getAnnotations()
                                                                        .get("kubernetes.io/change-cause")
                                                        : null);
                                        revision.put("replicas",
                                                        rs.getStatus() != null ? rs.getStatus().getReplicas() : 0);
                                        revision.put("createdAt", rs.getMetadata().getCreationTimestamp());
                                        return revision;
                                })
                                .sorted((a, b) -> {
                                        String revA = (String) a.get("revision");
                                        String revB = (String) b.get("revision");
                                        return revB.compareTo(revA); // Descending order
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Get rollout revision details
         */
        public Map<String, Object> getRolloutRevisionDetails(String clusterUid, String namespace,
                        String name, Integer revision) {
                log.info("Getting rollout revision {} details for deployment {}/{} in cluster {}",
                                revision, namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Get the target ReplicaSet
                ReplicaSet targetRs = client.apps().replicaSets()
                                .inNamespace(namespace)
                                .list()
                                .getItems()
                                .stream()
                                .filter(rs -> rs.getMetadata().getOwnerReferences() != null &&
                                                rs.getMetadata().getOwnerReferences().stream()
                                                                .anyMatch(ref -> ref.getName().equals(name))
                                                &&
                                                rs.getMetadata().getAnnotations() != null &&
                                                revision.toString().equals(rs.getMetadata().getAnnotations()
                                                                .get("deployment.kubernetes.io/revision")))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Revision " + revision + " not found"));

                Map<String, Object> details = new HashMap<>();
                details.put("revision", revision.toString());
                details.put("name", targetRs.getMetadata().getName());
                details.put("changeCause", targetRs.getMetadata().getAnnotations() != null
                                ? targetRs.getMetadata().getAnnotations().get("kubernetes.io/change-cause")
                                : null);
                details.put("createdAt", targetRs.getMetadata().getCreationTimestamp());
                details.put("replicas", targetRs.getStatus() != null ? targetRs.getStatus().getReplicas() : 0);
                details.put("template", targetRs.getSpec().getTemplate());

                // Serialize to YAML for display (hide managed fields)
                try {
                        targetRs.getMetadata().setManagedFields(null);
                        details.put("yaml", io.fabric8.kubernetes.client.utils.Serialization.asYaml(targetRs));
                } catch (Exception e) {
                        log.warn("Failed to serialize ReplicaSet to YAML: {}", e.getMessage());
                        details.put("yaml", null);
                }

                return details;
        }

        /**
         * Rollback to specific revision
         */
        public Map<String, String> rollbackToRevision(String clusterUid, String namespace,
                        String name, Integer revision) {
                log.info("Rolling back deployment {}/{} to revision {} in cluster {}",
                                namespace, name, revision, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Get the target ReplicaSet
                List<ReplicaSet> replicaSets = client.apps().replicaSets()
                                .inNamespace(namespace)
                                .list()
                                .getItems()
                                .stream()
                                .filter(rs -> rs.getMetadata().getOwnerReferences() != null &&
                                                rs.getMetadata().getOwnerReferences().stream()
                                                                .anyMatch(ref -> ref.getName().equals(name))
                                                &&
                                                rs.getMetadata().getAnnotations() != null &&
                                                revision.toString().equals(rs.getMetadata().getAnnotations()
                                                                .get("deployment.kubernetes.io/revision")))
                                .findFirst()
                                .stream()
                                .collect(Collectors.toList());

                if (replicaSets.isEmpty()) {
                        throw new RuntimeException("Revision " + revision + " not found");
                }

                ReplicaSet targetRs = replicaSets.get(0);

                // Update deployment with the target ReplicaSet's template
                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .edit(deployment -> {
                                        deployment.getSpec().setTemplate(targetRs.getSpec().getTemplate());
                                        return deployment;
                                });

                return Map.of(
                                "message", "Deployment rolled back to revision " + revision,
                                "deployment", name,
                                "namespace", namespace,
                                "revision", revision.toString());
        }

        /**
         * View pods managed by deployment
         */
        public List<Pod> viewPods(String clusterUid, String namespace, String name, Long clusterId) {
                log.info("Getting pods for deployment {}/{} in cluster {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // 1. Get the deployment to find labels
                io.fabric8.kubernetes.api.model.apps.Deployment deployment = client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .get();

                if (deployment == null || deployment.getSpec() == null || deployment.getSpec().getSelector() == null) {
                        log.warn("Deployment {}/{} not found or has no selector", namespace, name);
                        return List.of();
                }

                Map<String, String> selector = deployment.getSpec().getSelector().getMatchLabels();
                if (selector == null || selector.isEmpty()) {
                        log.warn("Deployment {}/{} has no matchLabels", namespace, name);
                        return List.of();
                }

                // 2. Query pods from database by clusterId, namespace AND labels
                // For simplicity, we filter the list from DB, but in a large system we'd use a
                // more efficient query
                List<Pod> allPodsInNamespace = podRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(clusterId,
                                namespace);

                return allPodsInNamespace.stream()
                                .filter(pod -> {
                                        if (pod.getLabels() == null)
                                                return false;
                                        // The pod must match ALL labels in the deployment selector
                                        try {
                                                Map<String, String> podLabels = io.fabric8.kubernetes.client.utils.Serialization
                                                                .unmarshal(pod.getLabels(), Map.class);
                                                return selector.entrySet().stream()
                                                                .allMatch(entry -> entry.getValue()
                                                                                .equals(podLabels.get(entry.getKey())));
                                        } catch (Exception e) {
                                                log.warn("Failed to parse pod labels JSON: {}", e.getMessage());
                                                return false;
                                        }
                                })
                                .collect(Collectors.toList());
        }
}
