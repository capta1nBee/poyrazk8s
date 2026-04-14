package com.k8s.platform.service.cluster;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterContextManager {

    private final ClusterRepository clusterRepository;
    private final Map<String, KubernetesClient> clients = new ConcurrentHashMap<>();

    public KubernetesClient getClient(String clusterUid) {
        return clients.computeIfAbsent(clusterUid, this::createClient);
    }

    public KubernetesClient getClient(Long clusterId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found with ID: " + clusterId));
        return getClient(cluster.getUid());
    }

    private KubernetesClient createClient(String clusterUid) {
        log.info("Creating Kubernetes client for cluster UID: {}", clusterUid);

        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        // Check if cluster is active (skip for new clusters during registration)
        if (!cluster.getIsActive()) {
            log.debug("Cluster is not active yet, but allowing client creation for registration: {}", clusterUid);
        }

        try {
            Config config;

            if (cluster.getKubeconfig() != null && !cluster.getKubeconfig().isEmpty()) {
                // Load from kubeconfig (raw YAML)
                config = Config.fromKubeconfig(cluster.getKubeconfig());
            } else {
                // Build from cluster properties
                config = new ConfigBuilder()
                        .withMasterUrl(cluster.getApiServer())
                        .build();
            }

            KubernetesClient client = new KubernetesClientBuilder()
                    .withConfig(config)
                    .build();

            log.info("Successfully created Kubernetes client for cluster: {} ({})", cluster.getName(), clusterUid);
            return client;

        } catch (Exception e) {
            log.error("Failed to create Kubernetes client for cluster UID: {}", clusterUid, e);
            throw new RuntimeException("Failed to create Kubernetes client: " + e.getMessage(), e);
        }
    }

    public void registerCluster(Cluster cluster) {
        log.info("Registering cluster: {} (UID: {})", cluster.getName(), cluster.getUid());

        // Test connection
        try {
            KubernetesClient client = createClient(cluster.getUid());
            clients.put(cluster.getUid(), client);

            // Test connection by getting version (Safely)
            try {
                String version = client.getKubernetesVersion().getGitVersion();
                log.info("Cluster {} connected successfully. Kubernetes version: {}", cluster.getName(), version);
            } catch (Exception e) {
                log.warn(
                        "Cluster {} connected, but failed to retrieve version info: {}. This might be due to unexpected fields in version response.",
                        cluster.getName(), e.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to register cluster: {}", cluster.getName(), e);
            throw new RuntimeException("Failed to connect to cluster: " + e.getMessage(), e);
        }
    }

    public void removeCluster(String clusterUid) {
        log.info("Removing cluster UID: {}", clusterUid);

        KubernetesClient client = clients.remove(clusterUid);
        if (client != null) {
            client.close();
            log.info("Cluster removed successfully: {}", clusterUid);
        }
    }

    public boolean testConnection(String clusterUid) {
        try {
            KubernetesClient client = getClient(clusterUid);
            client.getKubernetesVersion();
            return true;
        } catch (Exception e) {
            log.error("Connection test failed for cluster UID: {}", clusterUid, e);
            return false;
        }
    }

    public void initializeAllClusters() {
        log.info("Initializing all active clusters");

        clusterRepository.findByIsActiveTrue().forEach(cluster -> {
            try {
                registerCluster(cluster);
            } catch (Exception e) {
                log.error("Failed to initialize cluster: {}", cluster.getName(), e);
            }
        });
    }
}
