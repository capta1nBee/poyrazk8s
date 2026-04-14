package com.k8s.platform.service.cluster;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.dto.request.ClusterRequest;
import com.k8s.platform.service.k8s.K8sWatcherService;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.VersionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;
    private final ClusterSyncService clusterSyncService;
    private final K8sWatcherService k8sWatcherService;
    private final JdbcTemplate jdbcTemplate;

    public List<Cluster> getAllClusters() {
        return clusterRepository.findAll();
    }

    public List<Cluster> getActiveClusters() {
        return clusterRepository.findByIsActiveTrue();
    }

    public Cluster getCluster(Long id) {
        return clusterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
    }

    public Cluster getClusterByName(String name) {
        return clusterRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + name));
    }

    @Transactional
    public Cluster createCluster(ClusterRequest request) {
        log.info("Creating cluster: {}", request.getName());

        if (clusterRepository.existsByName(request.getName())) {
            throw new RuntimeException("Cluster already exists: " + request.getName());
        }

        // 1. VALIDATE KUBECONFIG AND GET CLUSTER INFO
        log.info("Validating kubeconfig for cluster: {}", request.getName());
        KubernetesClient tempClient;
        String kubeSystemUid;
        String apiServer;

        try {
            // Create temporary client to validate kubeconfig
            tempClient = createTempKubernetesClient(request);

            // Extract API server URL from the client config
            apiServer = tempClient.getConfiguration().getMasterUrl();
            log.info("Extracted API server URL: {}", apiServer);

            // If request has apiServer, use it; otherwise use extracted one
            if (request.getApiServer() == null || request.getApiServer().isEmpty()) {
                request.setApiServer(apiServer);
            }

            // Try to get version (optional - some clusters may have incompatible responses)
            try {
                String version = tempClient.getVersion().getGitVersion();

                VersionInfo versionx = tempClient.getVersion();

                System.out.println("Major: " + versionx.getMajor());
                System.out.println("Minor: " + versionx.getMinor());
                System.out.println("GitVersion: " + versionx.getGitVersion());
                System.out.println("Platform: " + versionx.getPlatform());

                log.info("Kubeconfig validated successfully. Kubernetes version: {}", version);
            } catch (Exception e) {
                log.warn("Could not retrieve Kubernetes version (non-critical): {} - {}",
                        e.getClass().getSimpleName(), e.getMessage());
                log.info("Proceeding with cluster registration despite version check failure");
            }

            // Get kube-system namespace UID as cluster identifier
            kubeSystemUid = getKubeSystemNamespaceUid(tempClient);
            if (kubeSystemUid == null) {
                throw new RuntimeException("Failed to get kube-system namespace UID");
            }
            log.info("Obtained cluster UID from kube-system namespace: {}", kubeSystemUid);

        } catch (Exception e) {
            log.error("Kubeconfig validation failed: {} - {}", e.getClass().getName(), e.getMessage(), e);
            throw new RuntimeException("Invalid kubeconfig or unable to connect to cluster: " + e.getMessage(), e);
        }

        // 2. SAVE CLUSTER TO DATABASE (initially inactive)
        Cluster cluster = Cluster.builder()
                .name(request.getName())
                .apiServer(request.getApiServer())
                .authType(request.getAuthType())
                .kubeconfig(request.getKubeconfig())
                .uid(kubeSystemUid) // Use kube-system namespace UID as cluster UID
                .isActive(false) // Initially inactive until fully synced
                .vulnScanEnabled(request.getVulnScanEnabled() != null ? request.getVulnScanEnabled() : false)
                .privateRegistryUser(request.getPrivateRegistryUser())
                .privateRegistryPassword(request.getPrivateRegistryPassword())
                .build();

        cluster = clusterRepository.save(cluster);
        log.info("Cluster saved to database with ID: {} and UID: {} (initially inactive)", cluster.getId(),
                cluster.getUid());

        // 3. REGISTER CLUSTER CONNECTION
        try {
            clusterContextManager.registerCluster(cluster);
            log.info("Cluster registered in context manager: {}", cluster.getName());
        } catch (Exception e) {
            log.error("Failed to register cluster, rolling back", e);
            clusterRepository.delete(cluster);
            throw new RuntimeException("Failed to connect to cluster: " + e.getMessage(), e);
        }

        // 4. SYNC CLUSTER DATA (only after successful registration and DB save)
        try {
            clusterSyncService.syncCluster(cluster);
            log.info("Cluster synced successfully: {}", cluster.getName());
        } catch (Exception e) {
            log.error("Failed to sync cluster: {}", cluster.getName(), e);
            clusterRepository.delete(cluster);
            throw new RuntimeException("Cluster sync failed: " + e.getMessage(), e);
        }

        // 5. ACTIVATE CLUSTER (after successful sync)
        cluster.setIsActive(true);
        cluster = clusterRepository.save(cluster);
        log.info("Cluster activated and ready: {}", cluster.getName());

        // 6. START WATCHERS (optional, can fail gracefully)
        try {
            k8sWatcherService.startWatchersForCluster(cluster);
            log.info("Watchers started for cluster: {}", cluster.getName());
        } catch (Exception e) {
            log.warn("Failed to start watchers for cluster: {}", cluster.getName(), e);
        }

        log.info("Cluster created and activated successfully: {}", cluster.getName());
        return cluster;
    }

    private KubernetesClient createTempKubernetesClient(ClusterRequest request) {
        try {
            Config config;
            if (request.getKubeconfig() != null && !request.getKubeconfig().isEmpty()) {
                String kubeconfig = request.getKubeconfig();

                // Try to decode from Base64 if necessary
                if (isBase64Encoded(kubeconfig)) {
                    try {
                        kubeconfig = new String(java.util.Base64.getDecoder().decode(kubeconfig));
                        log.debug("Kubeconfig was Base64 encoded, decoded successfully");
                    } catch (IllegalArgumentException e) {
                        log.debug("Kubeconfig is not valid Base64, treating as plain text");
                    }
                }

                log.debug("Attempting to parse kubeconfig");
                config = Config.fromKubeconfig(kubeconfig);
                log.debug("Kubeconfig parsed successfully");
            } else {
                log.debug("No kubeconfig provided, using API server URL: {}", request.getApiServer());
                config = new ConfigBuilder()
                        .withMasterUrl(request.getApiServer())
                        .build();
            }

            log.debug("Creating Kubernetes client");
            KubernetesClient client = new KubernetesClientBuilder()
                    .withConfig(config)
                    .build();
            log.debug("Kubernetes client created successfully");
            return client;

        } catch (Exception e) {
            log.error("Failed to create Kubernetes client: {} - {}", e.getClass().getName(), e.getMessage(), e);
            throw new RuntimeException("Invalid kubeconfig: " + e.getMessage(), e);
        }
    }

    private boolean isBase64Encoded(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            return java.util.Base64.getDecoder().decode(str).length > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String getKubeSystemNamespaceUid(KubernetesClient client) {
        try {
            log.debug("Fetching kube-system namespace");
            io.fabric8.kubernetes.api.model.Namespace kubeSystem = client.namespaces().withName("kube-system").get();

            if (kubeSystem == null) {
                log.error("kube-system namespace not found - cluster may not be properly initialized");
                return null;
            }

            if (kubeSystem.getMetadata() == null) {
                log.error("kube-system namespace metadata is null");
                return null;
            }

            String uid = kubeSystem.getMetadata().getUid();
            if (uid == null || uid.isEmpty()) {
                log.error("kube-system namespace UID is null or empty");
                return null;
            }

            log.info("Successfully retrieved kube-system namespace UID: {}", uid);
            return uid;

        } catch (Exception e) {
            log.error("Failed to get kube-system namespace UID: {} - {}", e.getClass().getName(), e.getMessage(), e);
            return null;
        }
    }

    @Transactional
    public Cluster updateCluster(Long id, ClusterRequest request) {
        log.info("Updating cluster: {}", id);

        Cluster cluster = getCluster(id);

        // 1. Remove old client
        clusterContextManager.removeCluster(cluster.getUid());
        log.info("Old cluster context removed");

        // 2. Update cluster properties
        cluster.setName(request.getName());
        cluster.setApiServer(request.getApiServer());
        cluster.setAuthType(request.getAuthType());
        if (request.getKubeconfig() != null && !request.getKubeconfig().isEmpty()) {
            cluster.setKubeconfig(request.getKubeconfig());
        }
        cluster.setVulnScanEnabled(request.getVulnScanEnabled() != null ? request.getVulnScanEnabled() : false);
        cluster.setPrivateRegistryUser(request.getPrivateRegistryUser());
        cluster.setPrivateRegistryPassword(request.getPrivateRegistryPassword());

        // 3. Save updated cluster to database
        cluster = clusterRepository.save(cluster);
        log.info("Cluster updated in database: {}", cluster.getName());

        // 4. Re-register cluster connection
        try {
            clusterContextManager.registerCluster(cluster);
            log.info("Cluster re-registered in context manager: {}", cluster.getName());
        } catch (Exception e) {
            log.error("Failed to re-register cluster: {}", cluster.getName(), e);
            throw new RuntimeException("Failed to connect to updated cluster: " + e.getMessage(), e);
        }

        // 5. Sync cluster data
        try {
            clusterSyncService.syncCluster(cluster);
            log.info("Cluster synced successfully: {}", cluster.getName());
        } catch (Exception e) {
            log.error("Failed to sync cluster: {}", cluster.getName(), e);
        }

        // 6. Start watchers
        try {
            k8sWatcherService.startWatchersForCluster(cluster);
            log.info("Watchers started for updated cluster: {}", cluster.getName());
        } catch (Exception e) {
            log.warn("Failed to start watchers for cluster: {}", cluster.getName(), e);
        }

        log.info("Cluster updated successfully: {}", cluster.getName());
        return cluster;
    }

    @Transactional
    public void deleteCluster(Long id) {
        log.info("Deleting cluster: {}", id);

        Cluster cluster = getCluster(id);

        // 1. Wipe all associated data from DB
        wipeClusterData(cluster.getId(), cluster.getUid());

        // 2. Remove from context manager
        clusterContextManager.removeCluster(cluster.getUid());

        // 3. Delete cluster record
        clusterRepository.delete(cluster);

        log.info("Cluster and all associated data deleted successfully: {}", cluster.getName());
    }

    private void wipeClusterData(Long clusterId, String clusterUid) {
        log.info("Wiping all data for cluster ID: {} and UID: {}", clusterId, clusterUid);

        // Tables using cluster_id (Long) as the primary cluster identifier
        String[] k8sTables = {
                "pods", "deployments", "services", "ingresses", "config_maps", "secrets",
                "persistent_volumes", "persistent_volume_claims", "daemonsets", "statefulsets",
                "jobs", "cronjobs", "k8s_events", "namespaces", "nodes",
                "replication_controllers", "replicasets", "endpoint_slices", "network_policies",
                "leases", "applications", "backups", "k8sroles", "cluster_roles",
                "role_bindings", "cluster_role_bindings", "service_accounts",
                "custom_resource_definitions", "ingress_classes", "csi_drivers", "csi_nodes",
                "certificate_signing_requests", "ip_addresses", "mutating_webhook_configurations",
                "priority_classes", "priority_level_configurations", "validating_admission_policies",
                "validating_admission_policy_bindings", "validating_webhook_configurations",
                "volume_attachments"
        };

        for (String table : k8sTables) {
            try {
                int deletedCount = jdbcTemplate.update("DELETE FROM " + table + " WHERE cluster_id = ?", clusterId);
                log.debug("Deleted {} records from {}", deletedCount, table);
            } catch (Exception e) {
                // Catching exception using JdbcTemplate does NOT mark the transaction as
                // rollback-only,
                // which is why we transitioned from EntityManager to JdbcTemplate here.
                log.warn("Could not wipe data from table {}: {}", table, e.getMessage());
            }
        }

        // Tables using cluster_id (String/UID)
        String[] uidTables = { "vulnerability_results", "exec_logs", "exec_sessions" };
        for (String table : uidTables) {
            try {
                int deletedCount = jdbcTemplate.update("DELETE FROM " + table + " WHERE cluster_id = ?", clusterUid);
                log.debug("Deleted {} records from {} using UID", deletedCount, table);
            } catch (Exception e) {
                log.warn("Could not wipe data from UID table {}: {}", table, e.getMessage());
            }
        }
    }

    public boolean testConnection(Long id) {
        Cluster cluster = getCluster(id);
        return clusterContextManager.testConnection(cluster.getUid());
    }

    public boolean testConnection(ClusterRequest request) {
        // Create temporary cluster for testing
        Cluster tempCluster = Cluster.builder()
                .name("temp-" + System.currentTimeMillis())
                .apiServer(request.getApiServer())
                .authType(request.getAuthType())
                .kubeconfig(request.getKubeconfig())
                .isActive(true)
                .build();

        try {
            clusterContextManager.registerCluster(tempCluster);
            clusterContextManager.removeCluster(tempCluster.getName());
            return true;
        } catch (Exception e) {
            log.error("Connection test failed", e);
            return false;
        }
    }

    /**
     * Validates kubeconfig without creating a cluster.
     * Returns validation details including cluster info.
     */
    public com.k8s.platform.dto.response.ClusterValidationResponse validateKubeconfig(ClusterRequest request) {
        log.info("Validating kubeconfig");

        try {
            // Create temporary client to validate kubeconfig
            KubernetesClient tempClient = createTempKubernetesClient(request);

            // Get version
            String version = "unknown";
            try {
                var versionInfo = tempClient.getKubernetesVersion();

                if (versionInfo != null) {
                    version = versionInfo.getGitVersion();
                }
                log.info("Kubernetes version: {}", version);
            } catch (Exception e) {
                log.warn("Could not retrieve Kubernetes version: {}", e.getMessage());
            }

            // Try to extract a better cluster name if the provided one is generic
            String clusterName = request.getName();
            if (clusterName == null || clusterName.isEmpty() || clusterName.equals("temp-cluster")) {
                try {
                    String contextName = tempClient.getConfiguration().getCurrentContext().getName();
                    if (contextName != null && !contextName.isEmpty()) {
                        clusterName = contextName;
                        log.info("Extracted cluster name from context: {}", clusterName);
                    }
                } catch (Exception e) {
                    log.debug("Could not extract cluster name from context");
                }
            }

            // Get kube-system namespace UID as cluster identifier
            String kubeSystemUid = getKubeSystemNamespaceUid(tempClient);
            if (kubeSystemUid == null) {
                return com.k8s.platform.dto.response.ClusterValidationResponse.failure(
                        "NAMESPACE_ERROR",
                        "Failed to retrieve kube-system namespace UID",
                        "The cluster may not be properly initialized");
            }

            // Get node count
            Integer nodeCount = 0;
            try {
                nodeCount = (int) tempClient.nodes().list().getItems().size();
                log.info("Node count: {}", nodeCount);
            } catch (Exception e) {
                log.warn("Could not retrieve node count: {}", e.getMessage());
            }

            // Get API server URL from the client configuration
            String apiServer = tempClient.getConfiguration().getMasterUrl();
            log.info("Extracted API server URL: {}", apiServer);

            log.info("Kubeconfig validation successful");
            return com.k8s.platform.dto.response.ClusterValidationResponse.success(
                    clusterName,
                    apiServer,
                    version,
                    kubeSystemUid,
                    nodeCount);

        } catch (Exception e) {
            log.error("Kubeconfig validation failed: {} - {}", e.getClass().getName(), e.getMessage());

            String errorType = "INVALID_KUBECONFIG";
            String message = "Failed to validate kubeconfig";
            String details = e.getMessage();

            if (e.getMessage().contains("certificate") || e.getMessage().contains("auth")) {
                errorType = "AUTHENTICATION_ERROR";
                message = "Authentication failed - check certificate or credentials";
            } else if (e.getMessage().contains("connection") || e.getMessage().contains("timeout")) {
                errorType = "CONNECTION_ERROR";
                message = "Failed to connect to API server";
            }

            return com.k8s.platform.dto.response.ClusterValidationResponse.failure(
                    errorType,
                    message,
                    details);
        }
    }
}
