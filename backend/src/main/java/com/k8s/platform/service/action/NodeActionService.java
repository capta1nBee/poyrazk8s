package com.k8s.platform.service.action;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NodeActionService {
    private final ClusterContextManager clusterContextManager;

    public void cordon(Long clusterId, String nodeName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            Node node = client.nodes().withName(nodeName).get();
            if (node == null) throw new RuntimeException("Node not found: " + nodeName);
            node.getSpec().setUnschedulable(true);
            client.nodes().withName(nodeName).replace(node);
            log.info("Cordoned node: {}", nodeName);
        } catch (Exception e) {
            log.error("Failed to cordon node: {}", nodeName, e);
            throw new RuntimeException("Failed to cordon node: " + e.getMessage(), e);
        }
    }

    public void uncordon(Long clusterId, String nodeName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            Node node = client.nodes().withName(nodeName).get();
            if (node == null) throw new RuntimeException("Node not found: " + nodeName);
            node.getSpec().setUnschedulable(false);
            client.nodes().withName(nodeName).replace(node);
            log.info("Uncordoned node: {}", nodeName);
        } catch (Exception e) {
            log.error("Failed to uncordon node: {}", nodeName, e);
            throw new RuntimeException("Failed to uncordon node: " + e.getMessage(), e);
        }
    }

    public void drain(Long clusterId, String nodeName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            cordon(clusterId, nodeName);
            client.pods().inAnyNamespace().withField("spec.nodeName", nodeName).delete();
            log.info("Drained node: {}", nodeName);
        } catch (Exception e) {
            log.error("Failed to drain node: {}", nodeName, e);
            throw new RuntimeException("Failed to drain node: " + e.getMessage(), e);
        }
    }
}
