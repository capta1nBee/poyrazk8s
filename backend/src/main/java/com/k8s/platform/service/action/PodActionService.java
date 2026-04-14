package com.k8s.platform.service.action;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PodActionService {

    private final ClusterContextManager clusterContextManager;

    public void restartPod(Long clusterId, String namespace, String podName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            
            // Delete the pod - it will be recreated by the controller
            client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .delete();
            
            log.info("Restarted pod: {}/{}", namespace, podName);
        } catch (Exception e) {
            log.error("Failed to restart pod: {}/{}", namespace, podName, e);
            throw new RuntimeException("Failed to restart pod: " + e.getMessage(), e);
        }
    }

    public void deletePod(Long clusterId, String namespace, String podName, Boolean force) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            
            if (Boolean.TRUE.equals(force)) {
                client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .withGracePeriod(0)
                    .delete();
            } else {
                client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .delete();
            }
            
            log.info("Deleted pod: {}/{} (force: {})", namespace, podName, force);
        } catch (Exception e) {
            log.error("Failed to delete pod: {}/{}", namespace, podName, e);
            throw new RuntimeException("Failed to delete pod: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getPodMetrics(Long clusterId, String namespace, String podName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            
            Pod pod = client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .get();
            
            if (pod == null) {
                throw new RuntimeException("Pod not found: " + podName);
            }
            
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("name", podName);
            metrics.put("namespace", namespace);
            metrics.put("phase", pod.getStatus().getPhase());
            metrics.put("nodeName", pod.getSpec().getNodeName());
            
            // Container metrics
            if (pod.getStatus().getContainerStatuses() != null) {
                int restartCount = pod.getStatus().getContainerStatuses().stream()
                    .mapToInt(cs -> cs.getRestartCount())
                    .sum();
                metrics.put("restartCount", restartCount);
            }
            
            return metrics;
        } catch (Exception e) {
            log.error("Failed to get pod metrics: {}/{}", namespace, podName, e);
            throw new RuntimeException("Failed to get pod metrics: " + e.getMessage(), e);
        }
    }
}

