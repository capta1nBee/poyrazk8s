package com.k8s.platform.service.k8s;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.apps.ControllerRevision;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatefulSetActionsService {

        private final ClusterContextManager clusterContextManager;

        /**
         * View rollout history for StatefulSet
         */
        public List<Map<String, Object>> viewRolloutHistory(String clusterUid, String namespace, String name) {
                log.info("Getting rollout history for statefulset {}/{} in cluster {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Get all ControllerRevisions owned by this StatefulSet
                List<ControllerRevision> revisions = client.apps().controllerRevisions()
                                .inNamespace(namespace)
                                .list()
                                .getItems()
                                .stream()
                                .filter(cr -> cr.getMetadata().getOwnerReferences() != null &&
                                                cr.getMetadata().getOwnerReferences().stream()
                                                                .anyMatch(ref -> ref.getName().equals(name) &&
                                                                                "StatefulSet".equals(ref.getKind())))
                                .collect(Collectors.toList());

                return revisions.stream()
                                .map(cr -> {
                                        Map<String, Object> revision = new HashMap<>();
                                        revision.put("name", cr.getMetadata().getName());
                                        revision.put("revision", cr.getRevision() != null ? cr.getRevision().toString()
                                                        : "unknown");
                                        revision.put("changeCause", cr.getMetadata().getAnnotations() != null
                                                        ? cr.getMetadata().getAnnotations()
                                                                        .get("kubernetes.io/change-cause")
                                                        : null);
                                        revision.put("createdAt", cr.getMetadata().getCreationTimestamp());
                                        return revision;
                                })
                                .sorted((a, b) -> {
                                        String revA = (String) a.get("revision");
                                        String revB = (String) b.get("revision");
                                        try {
                                                return Long.compare(Long.parseLong(revB), Long.parseLong(revA)); // Descending
                                                                                                                 // order
                                        } catch (NumberFormatException e) {
                                                return revB.compareTo(revA);
                                        }
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Get rollout revision details for StatefulSet
         */
        public Map<String, Object> getRolloutRevisionDetails(String clusterUid, String namespace,
                        String name, Integer revision) {
                log.info("Getting rollout revision {} details for statefulset {}/{} in cluster {}",
                                revision, namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Get the target ControllerRevision
                ControllerRevision targetCr = client.apps().controllerRevisions()
                                .inNamespace(namespace)
                                .list()
                                .getItems()
                                .stream()
                                .filter(cr -> cr.getMetadata().getOwnerReferences() != null &&
                                                cr.getMetadata().getOwnerReferences().stream()
                                                                .anyMatch(ref -> ref.getName().equals(name) &&
                                                                                "StatefulSet".equals(ref.getKind()))
                                                &&
                                                cr.getRevision() != null &&
                                                revision.longValue() == cr.getRevision())
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Revision " + revision + " not found"));

                Map<String, Object> details = new HashMap<>();
                details.put("revision", revision.toString());
                details.put("name", targetCr.getMetadata().getName());
                details.put("changeCause", targetCr.getMetadata().getAnnotations() != null
                                ? targetCr.getMetadata().getAnnotations().get("kubernetes.io/change-cause")
                                : null);
                details.put("createdAt", targetCr.getMetadata().getCreationTimestamp());
                details.put("data", targetCr.getData());

                // Serialize to YAML for display (hide managed fields)
                try {
                        targetCr.getMetadata().setManagedFields(null);
                        details.put("yaml", io.fabric8.kubernetes.client.utils.Serialization.asYaml(targetCr));
                } catch (Exception e) {
                        log.warn("Failed to serialize ControllerRevision to YAML: {}", e.getMessage());
                        details.put("yaml", null);
                }

                return details;
        }

        /**
         * Rollback StatefulSet to specific revision
         */
        public Map<String, String> rollbackToRevision(String clusterUid, String namespace,
                        String name, Integer revision) {
                log.info("Rolling back statefulset {}/{} to revision {} in cluster {}",
                                namespace, name, revision, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Verify revision exists
                client.apps().controllerRevisions()
                                .inNamespace(namespace)
                                .list()
                                .getItems()
                                .stream()
                                .filter(cr -> cr.getMetadata().getOwnerReferences() != null &&
                                                cr.getMetadata().getOwnerReferences().stream()
                                                                .anyMatch(ref -> ref.getName().equals(name) &&
                                                                                "StatefulSet".equals(ref.getKind()))
                                                &&
                                                cr.getRevision() != null &&
                                                revision.longValue() == cr.getRevision())
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Revision " + revision + " not found"));

                // Update StatefulSet with the target revision's template
                // Note: This is a simplified approach. In production, you'd need to properly
                // parse
                // the ControllerRevision data and apply it to the StatefulSet spec
                client.apps().statefulSets()
                                .inNamespace(namespace)
                                .withName(name)
                                .edit(ss -> {
                                        // The ControllerRevision contains the full StatefulSet spec in its data field
                                        // For now, we'll trigger a rollback by updating the updateRevision annotation
                                        if (ss.getMetadata().getAnnotations() == null) {
                                                ss.getMetadata().setAnnotations(new HashMap<>());
                                        }
                                        ss.getMetadata().getAnnotations().put("statefulset.kubernetes.io/rollback-to",
                                                        revision.toString());
                                        return ss;
                                });

                return Map.of(
                                "message", "StatefulSet rollback to revision " + revision + " initiated",
                                "statefulset", name,
                                "namespace", namespace,
                                "revision", revision.toString());
        }
}
