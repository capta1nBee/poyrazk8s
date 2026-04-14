package com.k8s.platform.service.backup;

import com.k8s.platform.domain.entity.Backup;
import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.BackupRepository;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final BackupRepository backupRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;

    @org.springframework.beans.factory.annotation.Value("${backup.base-path:/tmp/k8s-backup}")
    private String backupBasePath;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * Scheduled backup - runs every night at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledBackup() {
        log.info("Starting scheduled backup for all clusters");
        backupAllClusters("SCHEDULED");
    }

    /**
     * Backup all clusters that have backup enabled
     */
    public List<Backup> backupAllClusters(String triggeredBy) {
        List<Cluster> clusters = clusterRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .filter(c -> Boolean.TRUE.equals(c.getBackupEnabled()))
                .toList();

        List<Backup> backups = new ArrayList<>();
        for (Cluster cluster : clusters) {
            try {
                Backup backup = performBackup(cluster, triggeredBy);
                backups.add(backup);
            } catch (Exception e) {
                log.error("Failed to backup cluster {}: {}", cluster.getName(), e.getMessage());
            }
        }
        return backups;
    }

    /**
     * Backup a specific cluster
     */
    public Backup backupCluster(Long clusterId, String triggeredBy) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterId));
        return performBackup(cluster, triggeredBy);
    }

    /**
     * Backup a specific cluster by UID
     */
    public Backup backupClusterByUid(String clusterUid, String triggeredBy) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));
        return performBackup(cluster, triggeredBy);
    }

    /**
     * Perform the actual backup
     */
    @Async
    public Backup performBackup(Cluster cluster, String triggeredBy) {
        log.info("Starting backup for cluster: {}", cluster.getName());

        Backup backup = Backup.builder()
                .clusterId(cluster.getId())
                .clusterName(cluster.getName())
                .clusterUid(cluster.getUid())
                .status("RUNNING")
                .triggeredBy(triggeredBy)
                .startedAt(LocalDateTime.now())
                .build();
        backup = backupRepository.save(backup);

        try {
            KubernetesClient client = clusterContextManager.getClient(cluster.getId());

            String timestamp = LocalDateTime.now().format(DATE_FORMAT);
            String clusterPath = Paths.get(backupBasePath, cluster.getName(), timestamp).toString();

            // Create backup directory
            Files.createDirectories(Paths.get(clusterPath));

            AtomicInteger totalResources = new AtomicInteger(0);
            Set<String> namespaces = new HashSet<>();
            long totalSize = 0;

            // Backup cluster-scoped resources
            totalSize += backupClusterScopedResources(client, clusterPath, totalResources);

            // Get all namespaces
            List<Namespace> nsList = client.namespaces().list().getItems();

            // Backup namespace-scoped resources
            for (Namespace ns : nsList) {
                String nsName = ns.getMetadata().getName();
                namespaces.add(nsName);
                totalSize += backupNamespacedResources(client, clusterPath, nsName, totalResources);
            }

            // Calculate directory size
            totalSize = calculateDirectorySize(Paths.get(clusterPath));

            backup.setStatus("COMPLETED");
            backup.setBackupPath(clusterPath);
            backup.setTotalResources(totalResources.get());
            backup.setTotalNamespaces(namespaces.size());
            backup.setSizeBytes(totalSize);
            backup.setCompletedAt(LocalDateTime.now());

            log.info("Backup completed for cluster {}: {} resources, {} namespaces, {} bytes",
                    cluster.getName(), totalResources.get(), namespaces.size(), totalSize);

        } catch (Exception e) {
            log.error("Backup failed for cluster {}: {}", cluster.getName(), e.getMessage(), e);
            backup.setStatus("FAILED");
            backup.setErrorMessage(e.getMessage());
            backup.setCompletedAt(LocalDateTime.now());
        }

        return backupRepository.save(backup);
    }

    /**
     * Backup cluster-scoped resources
     */
    private long backupClusterScopedResources(KubernetesClient client, String basePath, AtomicInteger counter)
            throws IOException {
        long size = 0;
        String clusterScopedPath = Paths.get(basePath, "_cluster-scoped").toString();
        Files.createDirectories(Paths.get(clusterScopedPath));

        // Namespaces
        size += saveResources(client.namespaces().list().getItems(), clusterScopedPath, "namespaces", counter);

        // Nodes
        size += saveResources(client.nodes().list().getItems(), clusterScopedPath, "nodes", counter);

        // PersistentVolumes
        size += saveResources(client.persistentVolumes().list().getItems(), clusterScopedPath, "persistentvolumes",
                counter);

        // ClusterRoles
        size += saveResources(client.rbac().clusterRoles().list().getItems(), clusterScopedPath, "clusterroles",
                counter);

        // ClusterRoleBindings
        size += saveResources(client.rbac().clusterRoleBindings().list().getItems(), clusterScopedPath,
                "clusterrolebindings", counter);

        // StorageClasses
        try {
            size += saveResources(client.storage().v1().storageClasses().list().getItems(), clusterScopedPath,
                    "storageclasses", counter);
        } catch (Exception e) {
            log.debug("Could not backup storage classes: {}", e.getMessage());
        }

        // IngressClasses
        try {
            size += saveResources(client.network().v1().ingressClasses().list().getItems(), clusterScopedPath,
                    "ingressclasses", counter);
        } catch (Exception e) {
            log.debug("Could not backup ingress classes: {}", e.getMessage());
        }

        // PriorityClasses
        try {
            size += saveResources(client.scheduling().v1().priorityClasses().list().getItems(), clusterScopedPath,
                    "priorityclasses", counter);
        } catch (Exception e) {
            log.debug("Could not backup priority classes: {}", e.getMessage());
        }

        // CustomResourceDefinitions
        try {
            size += saveResources(client.apiextensions().v1().customResourceDefinitions().list().getItems(),
                    clusterScopedPath, "customresourcedefinitions", counter);
        } catch (Exception e) {
            log.debug("Could not backup CRDs: {}", e.getMessage());
        }

        return size;
    }

    /**
     * Backup namespace-scoped resources
     */
    private long backupNamespacedResources(KubernetesClient client, String basePath, String namespace,
            AtomicInteger counter) throws IOException {
        long size = 0;
        String nsPath = Paths.get(basePath, namespace).toString();
        Files.createDirectories(Paths.get(nsPath));

        // Pods
        size += saveResources(client.pods().inNamespace(namespace).list().getItems(), nsPath, "pods", counter);

        // Deployments
        size += saveResources(client.apps().deployments().inNamespace(namespace).list().getItems(), nsPath,
                "deployments", counter);

        // StatefulSets
        size += saveResources(client.apps().statefulSets().inNamespace(namespace).list().getItems(), nsPath,
                "statefulsets", counter);

        // DaemonSets
        size += saveResources(client.apps().daemonSets().inNamespace(namespace).list().getItems(), nsPath, "daemonsets",
                counter);

        // ReplicaSets
        size += saveResources(client.apps().replicaSets().inNamespace(namespace).list().getItems(), nsPath,
                "replicasets", counter);

        // Services
        size += saveResources(client.services().inNamespace(namespace).list().getItems(), nsPath, "services", counter);

        // ConfigMaps
        size += saveResources(client.configMaps().inNamespace(namespace).list().getItems(), nsPath, "configmaps",
                counter);

        // Secrets
        size += saveResources(client.secrets().inNamespace(namespace).list().getItems(), nsPath, "secrets", counter);

        // Ingresses
        try {
            size += saveResources(client.network().v1().ingresses().inNamespace(namespace).list().getItems(), nsPath,
                    "ingresses", counter);
        } catch (Exception e) {
            log.debug("Could not backup ingresses in {}: {}", namespace, e.getMessage());
        }

        // Jobs
        try {
            size += saveResources(client.batch().v1().jobs().inNamespace(namespace).list().getItems(), nsPath, "jobs",
                    counter);
        } catch (Exception e) {
            log.debug("Could not backup jobs in {}: {}", namespace, e.getMessage());
        }

        // CronJobs
        try {
            size += saveResources(client.batch().v1().cronjobs().inNamespace(namespace).list().getItems(), nsPath,
                    "cronjobs", counter);
        } catch (Exception e) {
            log.debug("Could not backup cronjobs in {}: {}", namespace, e.getMessage());
        }

        // PersistentVolumeClaims
        size += saveResources(client.persistentVolumeClaims().inNamespace(namespace).list().getItems(), nsPath,
                "persistentvolumeclaims", counter);

        // Roles
        size += saveResources(client.rbac().roles().inNamespace(namespace).list().getItems(), nsPath, "roles", counter);

        // RoleBindings
        size += saveResources(client.rbac().roleBindings().inNamespace(namespace).list().getItems(), nsPath,
                "rolebindings", counter);

        // ServiceAccounts
        size += saveResources(client.serviceAccounts().inNamespace(namespace).list().getItems(), nsPath,
                "serviceaccounts", counter);

        // NetworkPolicies
        try {
            size += saveResources(client.network().v1().networkPolicies().inNamespace(namespace).list().getItems(),
                    nsPath, "networkpolicies", counter);
        } catch (Exception e) {
            log.debug("Could not backup network policies in {}: {}", namespace, e.getMessage());
        }

        return size;
    }

    /**
     * Save resources to YAML files
     */
    private <T extends HasMetadata> long saveResources(List<T> resources, String basePath, String resourceType,
            AtomicInteger counter) throws IOException {
        if (resources == null || resources.isEmpty()) {
            return 0;
        }

        String resourcePath = Paths.get(basePath, resourceType).toString();
        Files.createDirectories(Paths.get(resourcePath));

        long totalSize = 0;
        for (T resource : resources) {
            try {
                // Clear managed fields for cleaner YAML
                if (resource.getMetadata() != null) {
                    resource.getMetadata().setManagedFields(null);
                }

                String name = resource.getMetadata().getName();
                String fileName = sanitizeFileName(name) + ".yaml";
                Path filePath = Paths.get(resourcePath, fileName);

                String yaml = Serialization.asYaml(resource);
                Files.writeString(filePath, yaml, StandardCharsets.UTF_8);

                totalSize += yaml.getBytes(StandardCharsets.UTF_8).length;
                counter.incrementAndGet();
            } catch (Exception e) {
                log.warn("Failed to save resource {}/{}: {}", resourceType,
                        resource.getMetadata().getName(), e.getMessage());
            }
        }

        return totalSize;
    }

    /**
     * Sanitize file name for filesystem
     */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    /**
     * Calculate directory size
     */
    private long calculateDirectorySize(Path path) {
        try {
            return Files.walk(path)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Get all backups (filtered by allowed clusters and optional specific cluster)
     */
    public List<Backup> getAllBackups(List<String> allowedClusterUids, String clusterUid) {
        List<Backup> all = backupRepository.findAllByOrderByCreatedAtDesc();

        return all.stream()
                .filter(b -> {
                    // Filter by authorization
                    if (allowedClusterUids != null && !allowedClusterUids.contains(b.getClusterUid())) {
                        return false;
                    }
                    // Filter by specific cluster selection
                    if (clusterUid != null && !clusterUid.equals(b.getClusterUid())) {
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    /**
     * Get backups for a cluster
     */
    public List<Backup> getBackupsByCluster(Long clusterId) {
        return backupRepository.findByClusterIdOrderByCreatedAtDesc(clusterId);
    }

    /**
     * Get backups for a cluster by UID
     */
    public List<Backup> getBackupsByClusterUid(String clusterUid) {
        return backupRepository.findByClusterUidOrderByCreatedAtDesc(clusterUid);
    }

    /**
     * Get backup by ID
     */
    public Optional<Backup> getBackupById(Long id) {
        return backupRepository.findById(id);
    }

    /**
     * Get backup statistics (filtered by allowed clusters and optional specific
     * cluster)
     */
    public Map<String, Object> getBackupStats(List<String> allowedClusterUids, String clusterUid) {
        List<Backup> backups = getAllBackups(allowedClusterUids, clusterUid);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBackups", (long) backups.size());
        stats.put("completedBackups", backups.stream().filter(b -> "COMPLETED".equals(b.getStatus())).count());
        stats.put("totalSize",
                backups.stream().filter(b -> "COMPLETED".equals(b.getStatus()) && b.getSizeBytes() != null)
                        .mapToLong(Backup::getSizeBytes).sum());

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        stats.put("lastWeekBackups",
                backups.stream().filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isAfter(weekAgo)).count());

        return stats;
    }

    /**
     * Delete old backups (keep last N backups per cluster)
     */
    public void cleanupOldBackups(int keepCount) {
        List<Cluster> clusters = clusterRepository.findAll();

        for (Cluster cluster : clusters) {
            List<Backup> backups = backupRepository.findCompletedBackupsByClusterId(cluster.getId());

            if (backups.size() > keepCount) {
                List<Backup> toDelete = backups.subList(keepCount, backups.size());

                for (Backup backup : toDelete) {
                    // Delete files
                    if (backup.getBackupPath() != null) {
                        try {
                            deleteDirectory(Paths.get(backup.getBackupPath()));
                        } catch (IOException e) {
                            log.warn("Failed to delete backup files: {}", e.getMessage());
                        }
                    }
                    // Delete record
                    backupRepository.delete(backup);
                }
            }
        }
    }

    /**
     * Delete directory recursively
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", p);
                        }
                    });
        }
    }

    /**
     * Get backup file content (for viewing)
     */
    public String getBackupFileContent(Long backupId, String relativePath) throws IOException {
        Backup backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new RuntimeException("Backup not found: " + backupId));

        Path filePath = Paths.get(backup.getBackupPath(), relativePath);

        // Security check - ensure path is within backup directory
        if (!filePath.normalize().startsWith(Paths.get(backup.getBackupPath()).normalize())) {
            throw new SecurityException("Invalid file path");
        }

        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * List backup files/directories
     */
    public List<Map<String, Object>> listBackupContents(Long backupId, String relativePath) throws IOException {
        Backup backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new RuntimeException("Backup not found: " + backupId));

        Path basePath = Paths.get(backup.getBackupPath());
        Path targetPath = relativePath != null && !relativePath.isEmpty()
                ? basePath.resolve(relativePath)
                : basePath;

        // Security check
        if (!targetPath.normalize().startsWith(basePath.normalize())) {
            throw new SecurityException("Invalid path");
        }

        List<Map<String, Object>> contents = new ArrayList<>();

        try (var stream = Files.list(targetPath)) {
            stream.forEach(path -> {
                Map<String, Object> item = new HashMap<>();
                item.put("name", path.getFileName().toString());
                item.put("isDirectory", Files.isDirectory(path));
                item.put("path", basePath.relativize(path).toString().replace("\\", "/"));
                try {
                    if (Files.isRegularFile(path)) {
                        item.put("size", Files.size(path));
                    }
                } catch (IOException e) {
                    item.put("size", 0);
                }
                contents.add(item);
            });
        }

        // Sort: directories first, then by name
        contents.sort((a, b) -> {
            boolean aDir = (boolean) a.get("isDirectory");
            boolean bDir = (boolean) b.get("isDirectory");
            if (aDir != bDir)
                return bDir ? 1 : -1;
            return ((String) a.get("name")).compareTo((String) b.get("name"));
        });

        return contents;
    }
}
