package com.k8s.platform.service.action;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
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
public class DaemonSetActionService {
    private final ClusterContextManager clusterContextManager;

    public void restart(Long clusterId, String namespace, String daemonSetName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            DaemonSet daemonSet = client.apps().daemonSets().inNamespace(namespace).withName(daemonSetName).get();
            if (daemonSet == null) throw new RuntimeException("DaemonSet not found: " + daemonSetName);
            Map<String, String> annotations = daemonSet.getSpec().getTemplate().getMetadata().getAnnotations();
            if (annotations == null) annotations = new HashMap<>();
            annotations.put("kubectl.kubernetes.io/restartedAt", OffsetDateTime.now().toString());
            daemonSet.getSpec().getTemplate().getMetadata().setAnnotations(annotations);
            client.apps().daemonSets().inNamespace(namespace).withName(daemonSetName).replace(daemonSet);
            log.info("Restart initiated for daemonset: {}/{}", namespace, daemonSetName);
        } catch (Exception e) {
            log.error("Failed to restart daemonset: {}/{}", namespace, daemonSetName, e);
            throw new RuntimeException("Failed to restart daemonset: " + e.getMessage(), e);
        }
    }
}
