package com.k8s.platform.service.action;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatefulSetActionService {
    private final ClusterContextManager clusterContextManager;

    public void scale(Long clusterId, String namespace, String statefulSetName, Integer replicas) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            client.apps().statefulSets().inNamespace(namespace).withName(statefulSetName).scale(replicas);
            log.info("Scaled statefulset {}/{} to {} replicas", namespace, statefulSetName, replicas);
        } catch (Exception e) {
            log.error("Failed to scale statefulset: {}/{}", namespace, statefulSetName, e);
            throw new RuntimeException("Failed to scale statefulset: " + e.getMessage(), e);
        }
    }

    public void restart(Long clusterId, String namespace, String statefulSetName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            StatefulSet statefulSet = client.apps().statefulSets().inNamespace(namespace).withName(statefulSetName).get();
            if (statefulSet == null) throw new RuntimeException("StatefulSet not found: " + statefulSetName);
            Map<String, String> annotations = statefulSet.getSpec().getTemplate().getMetadata().getAnnotations();
            if (annotations == null) annotations = new HashMap<>();
            annotations.put("kubectl.kubernetes.io/restartedAt", OffsetDateTime.now().toString());
            statefulSet.getSpec().getTemplate().getMetadata().setAnnotations(annotations);
            client.apps().statefulSets().inNamespace(namespace).withName(statefulSetName).replace(statefulSet);
            log.info("Restart initiated for statefulset: {}/{}", namespace, statefulSetName);
        } catch (Exception e) {
            log.error("Failed to restart statefulset: {}/{}", namespace, statefulSetName, e);
            throw new RuntimeException("Failed to restart statefulset: " + e.getMessage(), e);
        }
    }
}
