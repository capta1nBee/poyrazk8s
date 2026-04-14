package com.k8s.platform.service.k8s;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.Pod;
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
public class PodActionsService {

    private final ClusterContextManager clusterContextManager;

    /**
     * Restart pod by deleting it (controller will recreate)
     */
    public Map<String, String> restartPod(String clusterUid, String namespace, String name) {
        log.info("Restarting pod: {}/{} in cluster {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        
        Pod pod = client.pods().inNamespace(namespace).withName(name).get();
        
        if (pod == null) {
            throw new RuntimeException("Pod not found: " + name);
        }

        // Check if pod is managed by a controller
        if (pod.getMetadata().getOwnerReferences() == null || 
            pod.getMetadata().getOwnerReferences().isEmpty()) {
            throw new RuntimeException("Cannot restart standalone pod. Use delete instead.");
        }

        // Delete the pod - controller will recreate it
        client.pods().inNamespace(namespace).withName(name).delete();

        return Map.of(
                "message", "Pod restart initiated",
                "pod", name,
                "namespace", namespace
        );
    }

    /**
     * Get pod metrics (CPU, Memory usage)
     */
    public Map<String, Object> getPodMetrics(String clusterUid, String namespace, String name) {
        log.info("Getting metrics for pod: {}/{} in cluster {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        
        Pod pod = client.pods().inNamespace(namespace).withName(name).get();
        
        if (pod == null) {
            throw new RuntimeException("Pod not found: " + name);
        }

        // Get pod metrics from metrics API
        try {
            var metrics = client.top().pods().metrics(namespace, name);
            
            Map<String, Object> result = new HashMap<>();
            result.put("name", name);
            result.put("namespace", namespace);
            
            if (metrics != null && metrics.getContainers() != null) {
                List<Map<String, String>> containerMetrics = metrics.getContainers().stream()
                        .map(container -> Map.of(
                                "name", container.getName(),
                                "cpu", container.getUsage().get("cpu").toString(),
                                "memory", container.getUsage().get("memory").toString()
                        ))
                        .collect(Collectors.toList());
                
                result.put("containers", containerMetrics);
            } else {
                result.put("message", "Metrics not available. Ensure metrics-server is installed.");
            }
            
            return result;
        } catch (Exception e) {
            log.warn("Failed to get pod metrics: {}", e.getMessage());
            return Map.of(
                    "name", name,
                    "namespace", namespace,
                    "message", "Metrics not available: " + e.getMessage()
            );
        }
    }

    /**
     * Get list of containers in a pod
     */
    public List<Map<String, String>> getContainers(String clusterUid, String namespace, String name) {
        log.info("Getting containers for pod: {}/{} in cluster {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        
        Pod pod = client.pods().inNamespace(namespace).withName(name).get();
        
        if (pod == null) {
            throw new RuntimeException("Pod not found: " + name);
        }

        return pod.getSpec().getContainers().stream()
                .map(container -> {
                    Map<String, String> info = new HashMap<>();
                    info.put("name", container.getName());
                    info.put("image", container.getImage());
                    info.put("ready", getContainerReadyStatus(pod, container.getName()));
                    return info;
                })
                .collect(Collectors.toList());
    }

    /**
     * Port forward to pod (returns port forward info)
     */
    public Map<String, Object> createPortForward(String clusterUid, String namespace, 
                                                  String name, Integer localPort, Integer podPort) {
        log.info("Creating port forward for pod: {}/{} in cluster {} ({}:{})", 
                namespace, name, clusterUid, localPort, podPort);

        // Note: Actual port forwarding requires a persistent connection
        // This endpoint returns information needed for client-side port forwarding
        
        return Map.of(
                "message", "Port forward configuration",
                "pod", name,
                "namespace", namespace,
                "localPort", localPort,
                "podPort", podPort,
                "note", "Use kubectl port-forward or WebSocket connection for actual forwarding"
        );
    }

    private String getContainerReadyStatus(Pod pod, String containerName) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return "Unknown";
        }

        return pod.getStatus().getContainerStatuses().stream()
                .filter(status -> status.getName().equals(containerName))
                .findFirst()
                .map(status -> status.getReady() ? "Ready" : "Not Ready")
                .orElse("Unknown");
    }
}

