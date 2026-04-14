package com.k8s.platform.service.action;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalResourceActionService {
    private final ClusterContextManager clusterContextManager;

    public void deleteResource(Long clusterId, String namespace, String kind, String name) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            switch (kind.toLowerCase()) {
                case "pod" -> client.pods().inNamespace(namespace).withName(name).delete();
                case "deployment" -> client.apps().deployments().inNamespace(namespace).withName(name).delete();
                case "service" -> client.services().inNamespace(namespace).withName(name).delete();
                case "configmap" -> client.configMaps().inNamespace(namespace).withName(name).delete();
                case "secret" -> client.secrets().inNamespace(namespace).withName(name).delete();
                default -> throw new RuntimeException("Unsupported resource kind: " + kind);
            }
            log.info("Deleted {} {}/{}", kind, namespace, name);
        } catch (Exception e) {
            log.error("Failed to delete {} {}/{}", kind, namespace, name, e);
            throw new RuntimeException("Failed to delete resource: " + e.getMessage(), e);
        }
    }
}
