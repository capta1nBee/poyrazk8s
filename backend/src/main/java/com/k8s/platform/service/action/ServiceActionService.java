package com.k8s.platform.service.action;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceActionService {
    private final ClusterContextManager clusterContextManager;

    public void expose(Long clusterId, String namespace, String serviceName, String type) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            io.fabric8.kubernetes.api.model.Service service = client.services().inNamespace(namespace).withName(serviceName).get();
            if (service == null) throw new RuntimeException("Service not found: " + serviceName);
            service.getSpec().setType(type);
            client.services().inNamespace(namespace).withName(serviceName).replace(service);
            log.info("Exposed service {}/{} as {}", namespace, serviceName, type);
        } catch (Exception e) {
            log.error("Failed to expose service: {}/{}", namespace, serviceName, e);
            throw new RuntimeException("Failed to expose service: " + e.getMessage(), e);
        }
    }

    public void unexpose(Long clusterId, String namespace, String serviceName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            io.fabric8.kubernetes.api.model.Service service = client.services().inNamespace(namespace).withName(serviceName).get();
            if (service == null) throw new RuntimeException("Service not found: " + serviceName);
            service.getSpec().setType("ClusterIP");
            client.services().inNamespace(namespace).withName(serviceName).replace(service);
            log.info("Unexposed service {}/{} to ClusterIP", namespace, serviceName);
        } catch (Exception e) {
            log.error("Failed to unexpose service: {}/{}", namespace, serviceName, e);
            throw new RuntimeException("Failed to unexpose service: " + e.getMessage(), e);
        }
    }
}
