package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.K8sNode;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.K8sNodeRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NodeService {

    private final K8sNodeRepository nodeRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;

    public List<K8sNode> listNodes(String clusterUid) {
        return listNodes(clusterUid, false);
    }

    public List<K8sNode> listNodes(String clusterUid, boolean includeDeleted) {
        log.info("Listing all nodes in cluster UID: {}", clusterUid);
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        if (includeDeleted) {
            return nodeRepository.findByClusterId(cluster.getId());
        }
        return nodeRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    public K8sNode getNode(String clusterUid, String name) {
        log.info("Getting node: {} in cluster UID: {}", name, clusterUid);
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return nodeRepository.findByClusterIdAndNameAndIsDeletedFalse(cluster.getId(), name)
                .orElseThrow(() -> new RuntimeException("Node not found: " + name));
    }

    public Node cordonNode(String clusterUid, String name) {
        log.info("Cordoning node: {} in cluster UID: {}", name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        Node node = client.nodes().withName(name).get();
        if (node != null) {
            node.getSpec().setUnschedulable(true);
            return client.nodes().resource(node).update();
        }

        throw new RuntimeException("Node not found: " + name);
    }

    public Node uncordonNode(String clusterUid, String name) {
        log.info("Uncordoning node: {} in cluster UID: {}", name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        Node node = client.nodes().withName(name).get();
        if (node != null) {
            node.getSpec().setUnschedulable(false);
            return client.nodes().resource(node).update();
        }

        throw new RuntimeException("Node not found: " + name);
    }

    public void drainNode(String clusterUid, String name) {
        log.info("Draining node: {} in cluster UID: {}", name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        // First cordon the node
        cordonNode(clusterUid, name);

        // Delete all pods on this node (except DaemonSet pods)
        client.pods().inAnyNamespace().list().getItems().stream()
                .filter(pod -> pod.getSpec().getNodeName() != null && pod.getSpec().getNodeName().equals(name))
                .filter(pod -> pod.getMetadata().getOwnerReferences() == null ||
                        pod.getMetadata().getOwnerReferences().stream()
                                .noneMatch(ref -> "DaemonSet".equals(ref.getKind())))
                .forEach(pod -> client.pods()
                        .inNamespace(pod.getMetadata().getNamespace())
                        .withName(pod.getMetadata().getName())
                        .delete());

        log.info("Node drained successfully: {}", name);
    }

    public Map<String, Object> getNodeMetrics(String clusterUid, String name) {
        log.info("Getting metrics for node: {} in cluster UID: {}", name, clusterUid);

        K8sNode node = getNode(clusterUid, name);

        return Map.of(
                "name", name,
                "status", node.getStatus() != null ? node.getStatus() : "Unknown",
                "capacity", node.getCapacity() != null ? node.getCapacity() : "{}",
                "allocatable", node.getAllocatable() != null ? node.getAllocatable() : "{}");
    }
}
