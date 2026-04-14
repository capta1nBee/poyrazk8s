package com.k8s.platform.service.cluster;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.service.k8s.K8sResourceSyncService;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterSyncService {

    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;
    private final K8sResourceSyncService k8sResourceSyncService;

    // Single global lock to serialize sync operations and avoid SQLITE_BUSY
    private final ReentrantLock syncLock = new ReentrantLock(true);

    // Thread pool for async resource sync
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    @Scheduled(fixedDelayString = "${kubernetes.sync.fixed-delay:600000}") // Sync every 10 minutes (configurable)
    public void syncAllClusters() {
        log.info("Starting background cluster synchronization");
        boolean locked = false;
        try {
            locked = syncLock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Previous sync still running, skipping this scheduled run");
                return;
            }
            List<Cluster> activeClusters = clusterRepository.findByIsActiveTrue();
            log.info("Found {} active clusters to sync", activeClusters.size());
            activeClusters.forEach(cluster -> {
                try {
                    doSyncCluster(cluster);
                } catch (Exception e) {
                    log.error("Error syncing cluster {}: {}", cluster.getName(), e.getMessage());
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sync interrupted");
        } finally {
            if (locked)
                syncLock.unlock();
        }
    }

    public void syncCluster(Cluster cluster) {
        boolean locked = false;
        try {
            locked = syncLock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Could not acquire sync lock for cluster {}, skipping manual sync", cluster.getName());
                return;
            }
            doSyncCluster(cluster);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Manual sync interrupted for cluster {}", cluster.getName());
        } finally {
            if (locked)
                syncLock.unlock();
        }
    }

    private void doSyncCluster(Cluster cluster) {
        log.info("Syncing statistics for cluster: {}", cluster.getName());
        try {
            KubernetesClient client = clusterContextManager.getClient(cluster.getUid());

            // 1. Get Version
            try {
                String version = client.getKubernetesVersion().getGitVersion();
                if (version != null && !version.isEmpty()) {
                    cluster.setVersion(version);
                    log.info("Retrieved version {} for cluster {}", version, cluster.getName());
                } else {
                    log.warn("Version is null or empty for cluster {}", cluster.getName());
                }
            } catch (Exception e) {
                log.error("Failed to get version for cluster {}: {}", cluster.getName(), e.getMessage(), e);
                cluster.setVersion(null); // Explicitly set to null on failure
            }

            // 2. Get Nodes and calculate capacity
            List<Node> nodes = client.nodes().list().getItems();
            cluster.setNodes(nodes.size());

            long totalCpuMillis = 0;
            long totalMemoryBytes = 0;

            for (Node node : nodes) {
                var capacity = node.getStatus().getCapacity();
                if (capacity != null) {
                    Quantity cpu = capacity.get("cpu");
                    Quantity memory = capacity.get("memory");

                    if (cpu != null) {
                        totalCpuMillis += Quantity.getAmountInBytes(cpu).longValue() * 1000;
                    }
                    if (memory != null) {
                        totalMemoryBytes += Quantity.getAmountInBytes(memory).longValue();
                    }
                }
            }

            cluster.setCpu(formatCpu(totalCpuMillis));
            cluster.setMemory(formatMemory(totalMemoryBytes));
            cluster.setStatus("Ready");
            cluster.setProvider("Generic"); // Could be improved by checking node labels
            cluster.setUpdatedAt(LocalDateTime.now());

            // Save cluster statistics first before resource sync
            try {
                clusterRepository.save(cluster);
                safeSleep(100); // Brief delay to reduce lock contention
            } catch (Exception e) {
                log.warn("Failed to save cluster statistics: {}", e.getMessage());
            }

            // 3. Full Resource Synchronization (async to avoid blocking DB)
            syncAllResourcesAsync(cluster.getId(), client);

            log.info("Successfully synced cluster: {}", cluster.getName());

        } catch (Exception e) {
            log.error("Failed to sync cluster {}: {}", cluster.getName(), e.getMessage());
            try {
                cluster.setStatus("NotReady");
                clusterRepository.save(cluster);
            } catch (Exception ex) {
                log.error("Failed to update cluster status after sync failure: {}", ex.getMessage());
            }
        }
    }

    private void syncAllResources(Long clusterId, KubernetesClient client) {
        log.info("Starting full resource sync for cluster ID: {}", clusterId);

        // 0. Reconcile cluster state (mark deleted items as deleted)
        try {
            k8sResourceSyncService.reconcileCluster(clusterId, client);
        } catch (Exception e) {
            log.error("Failed to reconcile cluster {}: {}", clusterId, e.getMessage());
        }

        final int BATCH_SIZE = 10;
        final long BATCH_DELAY_MS = 50; // Small delay between batches to avoid connection exhaustion

        // Sync Nodes
        var nodes = client.nodes().list().getItems();
        log.info("Syncing {} nodes", nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            try {
                k8sResourceSyncService.syncNode(clusterId, nodes.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync node {}: {}", nodes.get(i).getMetadata().getName(), e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Pods
        var pods = client.pods().inAnyNamespace().list().getItems();
        log.info("Syncing {} pods", pods.size());
        for (int i = 0; i < pods.size(); i++) {
            try {
                k8sResourceSyncService.syncPod(clusterId, pods.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync pod {}/{}: {}", pods.get(i).getMetadata().getNamespace(),
                        pods.get(i).getMetadata().getName(), e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Deployments
        var deployments = client.apps().deployments().inAnyNamespace().list().getItems();
        log.info("Syncing {} deployments", deployments.size());
        for (int i = 0; i < deployments.size(); i++) {
            try {
                k8sResourceSyncService.syncDeployment(clusterId, deployments.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync deployment: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync DaemonSets
        var daemonSets = client.apps().daemonSets().inAnyNamespace().list().getItems();
        log.info("Syncing {} daemonsets", daemonSets.size());
        for (int i = 0; i < daemonSets.size(); i++) {
            try {
                k8sResourceSyncService.syncDaemonSet(clusterId, daemonSets.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync daemonset: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync StatefulSets
        var statefulSets = client.apps().statefulSets().inAnyNamespace().list().getItems();
        log.info("Syncing {} statefulsets", statefulSets.size());
        for (int i = 0; i < statefulSets.size(); i++) {
            try {
                k8sResourceSyncService.syncStatefulSet(clusterId, statefulSets.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync statefulset: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Services
        var services = client.services().inAnyNamespace().list().getItems();
        log.info("Syncing {} services", services.size());
        for (int i = 0; i < services.size(); i++) {
            try {
                k8sResourceSyncService.syncService(clusterId, services.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync service: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Ingresses
        var ingresses = client.network().v1().ingresses().inAnyNamespace().list().getItems();
        log.info("Syncing {} ingresses", ingresses.size());
        for (int i = 0; i < ingresses.size(); i++) {
            try {
                k8sResourceSyncService.syncIngress(clusterId, ingresses.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync ingress: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync CronJobs
        var cronJobs = client.batch().v1().cronjobs().inAnyNamespace().list().getItems();
        log.info("Syncing {} cronjobs", cronJobs.size());
        for (int i = 0; i < cronJobs.size(); i++) {
            try {
                k8sResourceSyncService.syncCronJob(clusterId, cronJobs.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync cronjob: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync ConfigMaps
        var configMaps = client.configMaps().inAnyNamespace().list().getItems();
        log.info("Syncing {} configmaps", configMaps.size());
        for (int i = 0; i < configMaps.size(); i++) {
            try {
                k8sResourceSyncService.syncConfigMap(clusterId, configMaps.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync configmap: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Secrets
        var secrets = client.secrets().inAnyNamespace().list().getItems();
        log.info("Syncing {} secrets", secrets.size());
        for (int i = 0; i < secrets.size(); i++) {
            try {
                k8sResourceSyncService.syncSecret(clusterId, secrets.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync secret: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync EndpointSlices
        var endpointSlices = client.discovery().v1().endpointSlices().inAnyNamespace().list().getItems();
        log.info("Syncing {} endpointslices", endpointSlices.size());
        for (int i = 0; i < endpointSlices.size(); i++) {
            try {
                k8sResourceSyncService.syncEndpointSlice(clusterId, endpointSlices.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync endpointslice: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Events
        var events = client.v1().events().inAnyNamespace().list().getItems();
        log.info("Syncing {} events", events.size());
        for (int i = 0; i < events.size(); i++) {
            try {
                k8sResourceSyncService.syncEvent(clusterId, events.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync event: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync PVCs
        var pvcs = client.persistentVolumeClaims().inAnyNamespace().list().getItems();
        log.info("Syncing {} PVCs", pvcs.size());
        for (int i = 0; i < pvcs.size(); i++) {
            try {
                k8sResourceSyncService.syncPVC(clusterId, pvcs.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync PVC: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync PersistentVolumes
        var pvs = client.persistentVolumes().list().getItems();
        log.info("Syncing {} PersistentVolumes", pvs.size());
        for (int i = 0; i < pvs.size(); i++) {
            try {
                k8sResourceSyncService.syncPersistentVolume(clusterId, pvs.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync PersistentVolume: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync ReplicaSets
        var replicaSets = client.apps().replicaSets().inAnyNamespace().list().getItems();
        log.info("Syncing {} ReplicaSets", replicaSets.size());
        for (int i = 0; i < replicaSets.size(); i++) {
            try {
                k8sResourceSyncService.syncReplicaSet(clusterId, replicaSets.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync ReplicaSet: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Namespaces
        var namespaces = client.namespaces().list().getItems();
        log.info("Syncing {} namespaces", namespaces.size());
        for (int i = 0; i < namespaces.size(); i++) {
            try {
                k8sResourceSyncService.syncNamespace(clusterId, namespaces.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync namespace: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Jobs
        var jobs = client.batch().v1().jobs().inAnyNamespace().list().getItems();
        log.info("Syncing {} jobs", jobs.size());
        for (int i = 0; i < jobs.size(); i++) {
            try {
                k8sResourceSyncService.syncJob(clusterId, jobs.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync job: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Leases
        var leases = client.leases().inAnyNamespace().list().getItems();
        log.info("Syncing {} leases", leases.size());
        for (int i = 0; i < leases.size(); i++) {
            try {
                k8sResourceSyncService.syncLease(clusterId, leases.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync lease: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync ClusterRoles
        var clusterRoles = client.rbac().clusterRoles().list().getItems();
        log.info("Syncing {} clusterRoles", clusterRoles.size());
        for (int i = 0; i < clusterRoles.size(); i++) {
            try {
                k8sResourceSyncService.syncClusterRole(clusterId, clusterRoles.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync clusterRole: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync ClusterRoleBindings
        var clusterRoleBindings = client.rbac().clusterRoleBindings().list().getItems();
        log.info("Syncing {} clusterRoleBindings", clusterRoleBindings.size());
        for (int i = 0; i < clusterRoleBindings.size(); i++) {
            try {
                k8sResourceSyncService.syncClusterRoleBinding(clusterId, clusterRoleBindings.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync clusterRoleBinding: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync Roles
        var roles = client.rbac().roles().inAnyNamespace().list().getItems();
        log.info("Syncing {} roles", roles.size());
        for (int i = 0; i < roles.size(); i++) {
            try {
                k8sResourceSyncService.syncRole(clusterId, roles.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync role: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync RoleBindings
        var roleBindings = client.rbac().roleBindings().inAnyNamespace().list().getItems();
        log.info("Syncing {} roleBindings", roleBindings.size());
        for (int i = 0; i < roleBindings.size(); i++) {
            try {
                k8sResourceSyncService.syncRoleBinding(clusterId, roleBindings.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync roleBinding: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync ServiceAccounts
        var serviceAccounts = client.serviceAccounts().inAnyNamespace().list().getItems();
        log.info("Syncing {} serviceAccounts", serviceAccounts.size());
        for (int i = 0; i < serviceAccounts.size(); i++) {
            try {
                k8sResourceSyncService.syncServiceAccount(clusterId, serviceAccounts.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync serviceAccount: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync MutatingWebhookConfigurations
        var mutatingWebhooks = client.admissionRegistration().v1().mutatingWebhookConfigurations().list().getItems();
        log.info("Syncing {} mutatingWebhookConfigurations", mutatingWebhooks.size());
        for (int i = 0; i < mutatingWebhooks.size(); i++) {
            try {
                k8sResourceSyncService.syncMutatingWebhookConfiguration(clusterId, mutatingWebhooks.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync mutatingWebhookConfiguration: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync ValidatingWebhookConfigurations
        var validatingWebhooks = client.admissionRegistration().v1().validatingWebhookConfigurations().list().getItems();
        log.info("Syncing {} validatingWebhookConfigurations", validatingWebhooks.size());
        for (int i = 0; i < validatingWebhooks.size(); i++) {
            try {
                k8sResourceSyncService.syncValidatingWebhookConfiguration(clusterId, validatingWebhooks.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync validatingWebhookConfiguration: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync CertificateSigningRequests
        var csrs = client.certificates().v1().certificateSigningRequests().list().getItems();
        log.info("Syncing {} certificateSigningRequests", csrs.size());
        for (int i = 0; i < csrs.size(); i++) {
            try {
                k8sResourceSyncService.syncCertificateSigningRequest(clusterId, csrs.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync certificateSigningRequest: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync CSIDrivers
        var csiDrivers = client.storage().v1().csiDrivers().list().getItems();
        log.info("Syncing {} csiDrivers", csiDrivers.size());
        for (int i = 0; i < csiDrivers.size(); i++) {
            try {
                k8sResourceSyncService.syncCSIDriver(clusterId, csiDrivers.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync csiDriver: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync CSINodes
        var csiNodes = client.storage().v1().csiNodes().list().getItems();
        log.info("Syncing {} csiNodes", csiNodes.size());
        for (int i = 0; i < csiNodes.size(); i++) {
            try {
                k8sResourceSyncService.syncCSINode(clusterId, csiNodes.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync csiNode: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync CustomResourceDefinitions
        var crds = client.apiextensions().v1().customResourceDefinitions().list().getItems();
        log.info("Syncing {} customResourceDefinitions", crds.size());
        for (int i = 0; i < crds.size(); i++) {
            try {
                k8sResourceSyncService.syncCustomResourceDefinition(clusterId, crds.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync customResourceDefinition: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync IngressClasses
        var ingressClasses = client.network().v1().ingressClasses().list().getItems();
        log.info("Syncing {} ingressClasses", ingressClasses.size());
        for (int i = 0; i < ingressClasses.size(); i++) {
            try {
                k8sResourceSyncService.syncIngressClass(clusterId, ingressClasses.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync ingressClass: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync IPAddresses (v1alpha1)
        try {
            var ipAddresses = client.network().v1alpha1().ipAddresses().list().getItems();
            log.info("Syncing {} ipAddresses", ipAddresses.size());
            for (int i = 0; i < ipAddresses.size(); i++) {
                try {
                    k8sResourceSyncService.syncIPAddress(clusterId, ipAddresses.get(i));
                    if ((i + 1) % BATCH_SIZE == 0) {
                        safeSleep(BATCH_DELAY_MS);
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync ipAddress: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("IPAddress API not available: {}", e.getMessage());
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync PriorityClasses
        var priorityClasses = client.scheduling().v1().priorityClasses().list().getItems();
        log.info("Syncing {} priorityClasses", priorityClasses.size());
        for (int i = 0; i < priorityClasses.size(); i++) {
            try {
                k8sResourceSyncService.syncPriorityClass(clusterId, priorityClasses.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync priorityClass: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync PriorityLevelConfigurations (v1beta3)
        try {
            var priorityLevelConfigs = client.flowControl().v1beta3().priorityLevelConfigurations().list().getItems();
            log.info("Syncing {} priorityLevelConfigurations", priorityLevelConfigs.size());
            for (int i = 0; i < priorityLevelConfigs.size(); i++) {
                try {
                    k8sResourceSyncService.syncPriorityLevelConfiguration(clusterId, priorityLevelConfigs.get(i));
                    if ((i + 1) % BATCH_SIZE == 0) {
                        safeSleep(BATCH_DELAY_MS);
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync priorityLevelConfiguration: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("PriorityLevelConfiguration API not available: {}", e.getMessage());
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync ValidatingAdmissionPolicies (v1beta1)
        try {
            var validatingAdmissionPolicies = client.admissionRegistration().v1beta1().validatingAdmissionPolicies().list().getItems();
            log.info("Syncing {} validatingAdmissionPolicies", validatingAdmissionPolicies.size());
            for (int i = 0; i < validatingAdmissionPolicies.size(); i++) {
                try {
                    k8sResourceSyncService.syncValidatingAdmissionPolicy(clusterId, validatingAdmissionPolicies.get(i));
                    if ((i + 1) % BATCH_SIZE == 0) {
                        safeSleep(BATCH_DELAY_MS);
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync validatingAdmissionPolicy: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("ValidatingAdmissionPolicy API not available: {}", e.getMessage());
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync ValidatingAdmissionPolicyBindings (v1beta1)
        try {
            var validatingAdmissionPolicyBindings = client.admissionRegistration().v1beta1().validatingAdmissionPolicyBindings().list().getItems();
            log.info("Syncing {} validatingAdmissionPolicyBindings", validatingAdmissionPolicyBindings.size());
            for (int i = 0; i < validatingAdmissionPolicyBindings.size(); i++) {
                try {
                    k8sResourceSyncService.syncValidatingAdmissionPolicyBinding(clusterId, validatingAdmissionPolicyBindings.get(i));
                    if ((i + 1) % BATCH_SIZE == 0) {
                        safeSleep(BATCH_DELAY_MS);
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync validatingAdmissionPolicyBinding: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("ValidatingAdmissionPolicyBinding API not available: {}", e.getMessage());
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync VolumeAttachments
        var volumeAttachments = client.storage().v1().volumeAttachments().list().getItems();
        log.info("Syncing {} volumeAttachments", volumeAttachments.size());
        for (int i = 0; i < volumeAttachments.size(); i++) {
            try {
                k8sResourceSyncService.syncVolumeAttachment(clusterId, volumeAttachments.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync volumeAttachment: {}", e.getMessage());
            }
        }
        safeSleep(BATCH_DELAY_MS);

        // Sync ReplicationControllers
        var replicationControllers = client.replicationControllers().inAnyNamespace().list().getItems();
        log.info("Syncing {} replicationControllers", replicationControllers.size());
        for (int i = 0; i < replicationControllers.size(); i++) {
            try {
                k8sResourceSyncService.syncReplicationController(clusterId, replicationControllers.get(i));
                if ((i + 1) % BATCH_SIZE == 0) {
                    safeSleep(BATCH_DELAY_MS);
                }
            } catch (Exception e) {
                log.warn("Failed to sync replicationController: {}", e.getMessage());
            }
        }

        log.info("Completed full resource sync for cluster ID: {}", clusterId);
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Sleep interrupted");
        }
    }

    private void syncAllResourcesAsync(Long clusterId, KubernetesClient client) {
        // Run resource sync in background to avoid blocking cluster statistics
        executorService.submit(() -> {
            try {
                safeSleep(500); // Stagger resource sync to avoid immediate lock
                syncAllResources(clusterId, client);
            } catch (Exception e) {
                log.warn("Background resource sync failed for cluster {}: {}", clusterId, e.getMessage());
            }
        });
    }

    private String formatCpu(long millis) {
        return (millis / 1000.0) + " Cores";
    }

    private String formatMemory(long bytes) {
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.2f GB", gb);
    }
}
