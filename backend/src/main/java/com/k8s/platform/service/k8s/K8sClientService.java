package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class K8sClientService {

    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;

    public Long getClusterIdByUid(String clusterUid) {
        return clusterRepository.findByUid(clusterUid)
                .map(Cluster::getId)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));
    }

    public KubernetesClient getClient(String clusterUid) {
        return clusterContextManager.getClient(clusterUid);
    }

    public KubernetesClient getClient(Long clusterId) {
        return clusterContextManager.getClient(clusterId);
    }

    /**
     * Serialize Kubernetes resource to YAML with managed fields hidden
     */
    public String serializeToYaml(Object resource) {
        // Hide managed fields for cleaner YAML output
        if (resource instanceof HasMetadata hasMetadata) {
            hasMetadata.getMetadata().setManagedFields(null);
        }
        return io.fabric8.kubernetes.client.utils.Serialization.asYaml(resource);
    }

    public <T> T deserializeFromYaml(String yaml, Class<T> clazz) {
        return io.fabric8.kubernetes.client.utils.Serialization.unmarshal(yaml, clazz);
    }
}
