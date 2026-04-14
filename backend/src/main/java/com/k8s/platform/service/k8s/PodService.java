package com.k8s.platform.service.k8s;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.Pod;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.PodRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PodService {

    @Value("${network.policy.policy-labels:}")
    private List<String> policyLabels;

    private final PodRepository podRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Pod> listPods(String clusterUid) {
        return listPods(clusterUid, false);
    }

    public List<Pod> listPods(String clusterUid, boolean includeDeleted) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        if (includeDeleted) {
            return podRepository.findByClusterId(cluster.getId());
        }
        return podRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    public List<Pod> listPods(String clusterUid, String namespace) {
        return listPods(clusterUid, namespace, false);
    }

    public List<Pod> listPods(String clusterUid, String namespace, boolean includeDeleted) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        if (includeDeleted) {
            return podRepository.findByClusterIdAndNamespace(cluster.getId(), namespace);
        }
        return podRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
    }

    public Pod getPod(String clusterUid, String namespace, String name) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return podRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(
                cluster.getId(), namespace, name)
                .orElseThrow(() -> new RuntimeException("Pod not found: " + name));
    }

    public void deletePod(String clusterUid, String namespace, String name) {
        log.info("Deleting pod: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        client.pods()
                .inNamespace(namespace)
                .withName(name)
                .delete();

        log.info("Pod deleted successfully: {}/{}", namespace, name);
    }

    public void forceDeletePod(String clusterUid, String namespace, String name) {
        log.info("Force deleting pod: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        client.pods()
                .inNamespace(namespace)
                .withName(name)
                .withGracePeriod(0)
                .delete();

        log.info("Pod force deleted successfully: {}/{}", namespace, name);
    }

    public String getLogs(String clusterUid, String namespace, String name, String container) {
        log.info("Getting logs for pod: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        var podResource = client.pods()
                .inNamespace(namespace)
                .withName(name);

        if (container != null && !container.isEmpty()) {
            return podResource.inContainer(container).getLog();
        } else {
            return podResource.getLog();
        }
    }

    public String getPreviousLogs(String clusterUid, String namespace, String name, String container) {
        log.info("Getting previous logs for pod: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        var podResource = client.pods()
                .inNamespace(namespace)
                .withName(name);

        if (container != null && !container.isEmpty()) {
            return podResource.inContainer(container).terminated().getLog();
        } else {
            return podResource.terminated().getLog();
        }
    }

    public List<Pod> listPodsByPhase(String clusterUid, String phase) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return podRepository.findByClusterIdAndPhaseAndIsDeletedFalse(cluster.getId(), phase);
    }

    public List<Pod> listPodsByNode(String clusterUid, String nodeName) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return podRepository.findByClusterIdAndNodeNameAndIsDeletedFalse(cluster.getId(), nodeName);
    }

    /**
     * Returns distinct key:value pairs for pod labels that match the configured
     * 'network.policy.policy-labels' keys (e.g., app, uygulama, deploy) across all
     * pods in the given cluster+namespace.
     */
    public List<String> getPodPolicyLabelValues(String clusterUid, String namespace) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        List<Pod> pods = podRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
        Set<String> values = new LinkedHashSet<>();

        for (Pod pod : pods) {
            if (pod.getLabels() == null || pod.getLabels().isBlank())
                continue;
            try {
                Map<String, String> labelMap = objectMapper.readValue(
                        pod.getLabels(), new TypeReference<Map<String, String>>() {
                        });

                // For this pod, find all labels where key matches our policyLabels
                if (policyLabels != null) {
                    for (Map.Entry<String, String> entry : labelMap.entrySet()) {
                        if (policyLabels.contains(entry.getKey())) {
                            // Add "key=value" formatted string to list
                            values.add(entry.getKey() + "=" + entry.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse labels for pod {}/{}: {}", namespace, pod.getName(), e.getMessage());
            }
        }

        return new ArrayList<>(values);
    }
}
