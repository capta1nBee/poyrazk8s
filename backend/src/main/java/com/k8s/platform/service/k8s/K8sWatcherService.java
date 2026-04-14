package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
@RequiredArgsConstructor
public class K8sWatcherService {

    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;
    private final K8sResourceSyncService k8sResourceSyncService;
    private final Map<String, Watch> activeWatches = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @PostConstruct
    public void init() {
        log.info("Initializing Kubernetes watchers for all active clusters");
        startWatchers();
    }

    public void startWatchers() {
        clusterRepository.findByIsActiveTrue().forEach(this::startWatchersForCluster);
    }

    public void startWatchersForCluster(Cluster cluster) {
        String clusterUid = cluster.getUid();

        executorService.submit(() -> {
            try {
                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Reconcile cluster state (mark deleted items as deleted)
                try {
                    k8sResourceSyncService.reconcileCluster(cluster.getId(), client);
                } catch (Exception e) {
                    log.error("Failed to reconcile cluster {}: {}", cluster.getName(), e.getMessage());
                }

                // Start watchers for all requested resource types
                watchResource(cluster, "Pods", client.pods().inAnyNamespace());
                watchResource(cluster, "Deployments", client.apps().deployments().inAnyNamespace());
                watchResource(cluster, "DaemonSets", client.apps().daemonSets().inAnyNamespace());
                watchResource(cluster, "StatefulSets", client.apps().statefulSets().inAnyNamespace());
                watchResource(cluster, "Services", client.services().inAnyNamespace());
                watchResource(cluster, "Ingresses", client.network().v1().ingresses().inAnyNamespace());
                watchResource(cluster, "PVCs", client.persistentVolumeClaims().inAnyNamespace());
                watchResource(cluster, "PVs", client.persistentVolumes());
                watchResource(cluster, "Jobs", client.batch().v1().jobs().inAnyNamespace());
                watchResource(cluster, "CronJobs", client.batch().v1().cronjobs().inAnyNamespace());
                watchResource(cluster, "ConfigMaps", client.configMaps().inAnyNamespace());
                watchResource(cluster, "Secrets", client.secrets().inAnyNamespace());
                watchResource(cluster, "Nodes", client.nodes());
                watchResource(cluster, "EndpointSlices", client.discovery().v1().endpointSlices().inAnyNamespace());
                watchResource(cluster, "Events", client.v1().events().inAnyNamespace());
                watchResource(cluster, "Namespaces", client.namespaces());
                watchResource(cluster, "ReplicaSets", client.apps().replicaSets().inAnyNamespace());
                watchResource(cluster, "Leases", client.leases().inAnyNamespace());
                watchResource(cluster, "ClusterRoles", client.rbac().clusterRoles());
                watchResource(cluster, "ClusterRoleBindings", client.rbac().clusterRoleBindings());
                watchResource(cluster, "Roles", client.rbac().roles().inAnyNamespace());
                watchResource(cluster, "RoleBindings", client.rbac().roleBindings().inAnyNamespace());
                watchResource(cluster, "ServiceAccounts", client.serviceAccounts().inAnyNamespace());
                watchResource(cluster, "MutatingWebhookConfigurations", client.admissionRegistration().v1().mutatingWebhookConfigurations());
                watchResource(cluster, "ValidatingWebhookConfigurations", client.admissionRegistration().v1().validatingWebhookConfigurations());
                watchResource(cluster, "CertificateSigningRequests", client.certificates().v1().certificateSigningRequests());
                watchResource(cluster, "CSIDrivers", client.storage().v1().csiDrivers());
                watchResource(cluster, "CSINodes", client.storage().v1().csiNodes());
                watchResource(cluster, "CustomResourceDefinitions", client.apiextensions().v1().customResourceDefinitions());
                watchResource(cluster, "IngressClasses", client.network().v1().ingressClasses());
                watchResource(cluster, "PriorityClasses", client.scheduling().v1().priorityClasses());
                watchResource(cluster, "VolumeAttachments", client.storage().v1().volumeAttachments());
                watchResource(cluster, "ReplicationControllers", client.replicationControllers().inAnyNamespace());
                watchResource(cluster, "HPAs", client.autoscaling().v2().horizontalPodAutoscalers().inAnyNamespace());

                // Watch optional/alpha APIs with error handling
                try {
                    watchResource(cluster, "IPAddresses", client.network().v1alpha1().ipAddresses());
                } catch (Exception e) {
                    log.warn("IPAddress API not available for cluster {}: {}", cluster.getName(), e.getMessage());
                }
                try {
                    watchResource(cluster, "PriorityLevelConfigurations", client.flowControl().v1beta3().priorityLevelConfigurations());
                } catch (Exception e) {
                    log.warn("PriorityLevelConfiguration API not available for cluster {}: {}", cluster.getName(), e.getMessage());
                }
                try {
                    watchResource(cluster, "ValidatingAdmissionPolicies", client.admissionRegistration().v1beta1().validatingAdmissionPolicies());
                } catch (Exception e) {
                    log.warn("ValidatingAdmissionPolicy API not available for cluster {}: {}", cluster.getName(), e.getMessage());
                }
                try {
                    watchResource(cluster, "ValidatingAdmissionPolicyBindings", client.admissionRegistration().v1beta1().validatingAdmissionPolicyBindings());
                } catch (Exception e) {
                    log.warn("ValidatingAdmissionPolicyBinding API not available for cluster {}: {}", cluster.getName(), e.getMessage());
                }

            } catch (Exception e) {
                log.error("Failed to start watchers for cluster {}: {}", cluster.getName(), e.getMessage());
            }
        });
    }

    private <T extends HasMetadata> void watchResource(Cluster cluster, String resourceType,
            io.fabric8.kubernetes.client.dsl.Watchable<T> watchable) {
        String watchKey = cluster.getUid() + "-" + resourceType.toLowerCase();

        if (activeWatches.containsKey(watchKey)) {
            return;
        }

        Watch watch = watchable.watch(new Watcher<T>() {
            @Override
            public void eventReceived(Action action, T resource) {
                log.info("Cluster {}: [{}] {} {} in namespace {}",
                        cluster.getName(), action, resourceType, resource.getMetadata().getName(),
                        resource.getMetadata().getNamespace() != null ? resource.getMetadata().getNamespace() : "N/A");

                try {
                    handleResourceSync(cluster.getId(), action, resourceType, resource);
                } catch (Exception e) {
                    log.error("Failed to sync {} {} for cluster {}: {}", resourceType, resource.getMetadata().getName(),
                            cluster.getName(), e.getMessage());
                }
            }

            @Override
            public void onClose(WatcherException cause) {
                if (cause != null) {
                    log.error("Watch for {} closed with error on cluster {}: {}",
                            resourceType, cluster.getName(), cause.getMessage());
                    activeWatches.remove(watchKey);
                }
            }
        });

        activeWatches.put(watchKey, watch);
        log.info("Started {} watcher for cluster: {}", resourceType, cluster.getName());
    }

    private void handleResourceSync(Long clusterId, Watcher.Action action, String resourceType, HasMetadata resource) {
        String uid = resource.getMetadata().getUid();

        if (action == Watcher.Action.DELETED) {
            switch (resourceType) {
                case "Pods" -> k8sResourceSyncService.markPodDeleted(uid);
                case "Deployments" -> k8sResourceSyncService.markDeploymentDeleted(uid);
                case "DaemonSets" -> k8sResourceSyncService.markDaemonSetDeleted(uid);
                case "StatefulSets" -> k8sResourceSyncService.markStatefulSetDeleted(uid);
                case "Services" -> k8sResourceSyncService.markServiceDeleted(uid);
                case "Ingresses" -> k8sResourceSyncService.markIngressDeleted(uid);
                case "CronJobs" -> k8sResourceSyncService.markCronJobDeleted(uid);
                case "Jobs" -> k8sResourceSyncService.markJobDeleted(uid);
                case "ConfigMaps" -> k8sResourceSyncService.markConfigMapDeleted(uid);
                case "Secrets" -> k8sResourceSyncService.markSecretDeleted(uid);
                case "Nodes" -> k8sResourceSyncService.markNodeDeleted(uid);
                case "EndpointSlices" -> k8sResourceSyncService.markEndpointSliceDeleted(uid);
                case "Events" -> k8sResourceSyncService.markEventDeleted(uid);
                case "Namespaces" -> k8sResourceSyncService.markNamespaceDeleted(uid);
                case "PVCs", "PersistentVolumeClaims" -> k8sResourceSyncService.markPVCDeleted(uid);
                case "PVs" -> k8sResourceSyncService.markPVDeleted(uid);
                case "ReplicaSets" -> k8sResourceSyncService.markReplicaSetDeleted(uid);
                case "Leases" -> k8sResourceSyncService.markLeaseDeleted(uid);
                case "ClusterRoles" -> k8sResourceSyncService.markClusterRoleDeleted(uid);
                case "ClusterRoleBindings" -> k8sResourceSyncService.markClusterRoleBindingDeleted(uid);
                case "Roles" -> k8sResourceSyncService.markRoleDeleted(uid);
                case "RoleBindings" -> k8sResourceSyncService.markRoleBindingDeleted(uid);
                case "ServiceAccounts" -> k8sResourceSyncService.markServiceAccountDeleted(uid);
                case "MutatingWebhookConfigurations" -> k8sResourceSyncService.markMutatingWebhookConfigurationDeleted(uid);
                case "ValidatingWebhookConfigurations" -> k8sResourceSyncService.markValidatingWebhookConfigurationDeleted(uid);
                case "CertificateSigningRequests" -> k8sResourceSyncService.markCertificateSigningRequestDeleted(uid);
                case "CSIDrivers" -> k8sResourceSyncService.markCSIDriverDeleted(uid);
                case "CSINodes" -> k8sResourceSyncService.markCSINodeDeleted(uid);
                case "CustomResourceDefinitions" -> k8sResourceSyncService.markCustomResourceDefinitionDeleted(uid);
                case "IngressClasses" -> k8sResourceSyncService.markIngressClassDeleted(uid);
                case "IPAddresses" -> k8sResourceSyncService.markIPAddressDeleted(uid);
                case "PriorityClasses" -> k8sResourceSyncService.markPriorityClassDeleted(uid);
                case "PriorityLevelConfigurations" -> k8sResourceSyncService.markPriorityLevelConfigurationDeleted(uid);
                case "ValidatingAdmissionPolicies" -> k8sResourceSyncService.markValidatingAdmissionPolicyDeleted(uid);
                case "ValidatingAdmissionPolicyBindings" -> k8sResourceSyncService.markValidatingAdmissionPolicyBindingDeleted(uid);
                case "VolumeAttachments" -> k8sResourceSyncService.markVolumeAttachmentDeleted(uid);
                case "ReplicationControllers" -> k8sResourceSyncService.markReplicationControllerDeleted(uid);
                case "HPAs" -> k8sResourceSyncService.markHpaDeleted(uid);
                default -> log.warn("Deletion sync not implemented for resource type: {}", resourceType);
            }
        } else {
            // ADDED, MODIFIED
            switch (resourceType) {
                case "Pods" ->
                    k8sResourceSyncService.syncPod(clusterId, (io.fabric8.kubernetes.api.model.Pod) resource);
                case "Deployments" -> k8sResourceSyncService.syncDeployment(clusterId,
                        (io.fabric8.kubernetes.api.model.apps.Deployment) resource);
                case "DaemonSets" -> k8sResourceSyncService.syncDaemonSet(clusterId,
                        (io.fabric8.kubernetes.api.model.apps.DaemonSet) resource);
                case "StatefulSets" -> k8sResourceSyncService.syncStatefulSet(clusterId,
                        (io.fabric8.kubernetes.api.model.apps.StatefulSet) resource);
                case "Services" ->
                    k8sResourceSyncService.syncService(clusterId, (io.fabric8.kubernetes.api.model.Service) resource);
                case "Ingresses" -> k8sResourceSyncService.syncIngress(clusterId,
                        (io.fabric8.kubernetes.api.model.networking.v1.Ingress) resource);
                case "CronJobs" -> k8sResourceSyncService.syncCronJob(clusterId,
                        (io.fabric8.kubernetes.api.model.batch.v1.CronJob) resource);
                case "Jobs" -> k8sResourceSyncService.syncJob(clusterId,
                        (io.fabric8.kubernetes.api.model.batch.v1.Job) resource);
                case "ConfigMaps" -> k8sResourceSyncService.syncConfigMap(clusterId,
                        (io.fabric8.kubernetes.api.model.ConfigMap) resource);
                case "Secrets" ->
                    k8sResourceSyncService.syncSecret(clusterId, (io.fabric8.kubernetes.api.model.Secret) resource);
                case "Nodes" ->
                    k8sResourceSyncService.syncNode(clusterId, (io.fabric8.kubernetes.api.model.Node) resource);
                case "EndpointSlices" -> k8sResourceSyncService.syncEndpointSlice(clusterId,
                        (io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice) resource);
                case "Events" ->
                    k8sResourceSyncService.syncEvent(clusterId, (io.fabric8.kubernetes.api.model.Event) resource);
                case "Namespaces" -> k8sResourceSyncService.syncNamespace(clusterId,
                        (io.fabric8.kubernetes.api.model.Namespace) resource);
                case "PVCs", "PersistentVolumeClaims" -> k8sResourceSyncService.syncPVC(clusterId,
                        (io.fabric8.kubernetes.api.model.PersistentVolumeClaim) resource);
                case "PVs" -> k8sResourceSyncService.syncPersistentVolume(clusterId,
                        (io.fabric8.kubernetes.api.model.PersistentVolume) resource);
                case "ReplicaSets" -> k8sResourceSyncService.syncReplicaSet(clusterId,
                        (io.fabric8.kubernetes.api.model.apps.ReplicaSet) resource);
                case "Leases" -> k8sResourceSyncService.syncLease(clusterId,
                        (io.fabric8.kubernetes.api.model.coordination.v1.Lease) resource);
                case "ClusterRoles" -> k8sResourceSyncService.syncClusterRole(clusterId,
                        (io.fabric8.kubernetes.api.model.rbac.ClusterRole) resource);
                case "ClusterRoleBindings" -> k8sResourceSyncService.syncClusterRoleBinding(clusterId,
                        (io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding) resource);
                case "Roles" -> k8sResourceSyncService.syncRole(clusterId,
                        (io.fabric8.kubernetes.api.model.rbac.Role) resource);
                case "RoleBindings" -> k8sResourceSyncService.syncRoleBinding(clusterId,
                        (io.fabric8.kubernetes.api.model.rbac.RoleBinding) resource);
                case "ServiceAccounts" -> k8sResourceSyncService.syncServiceAccount(clusterId,
                        (io.fabric8.kubernetes.api.model.ServiceAccount) resource);
                case "MutatingWebhookConfigurations" -> k8sResourceSyncService.syncMutatingWebhookConfiguration(clusterId,
                        (io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfiguration) resource);
                case "ValidatingWebhookConfigurations" -> k8sResourceSyncService.syncValidatingWebhookConfiguration(clusterId,
                        (io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration) resource);
                case "CertificateSigningRequests" -> k8sResourceSyncService.syncCertificateSigningRequest(clusterId,
                        (io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest) resource);
                case "CSIDrivers" -> k8sResourceSyncService.syncCSIDriver(clusterId,
                        (io.fabric8.kubernetes.api.model.storage.CSIDriver) resource);
                case "CSINodes" -> k8sResourceSyncService.syncCSINode(clusterId,
                        (io.fabric8.kubernetes.api.model.storage.CSINode) resource);
                case "CustomResourceDefinitions" -> k8sResourceSyncService.syncCustomResourceDefinition(clusterId,
                        (io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition) resource);
                case "IngressClasses" -> k8sResourceSyncService.syncIngressClass(clusterId,
                        (io.fabric8.kubernetes.api.model.networking.v1.IngressClass) resource);
                case "IPAddresses" -> k8sResourceSyncService.syncIPAddress(clusterId,
                        (io.fabric8.kubernetes.api.model.networking.v1alpha1.IPAddress) resource);
                case "PriorityClasses" -> k8sResourceSyncService.syncPriorityClass(clusterId,
                        (io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClass) resource);
                case "PriorityLevelConfigurations" -> k8sResourceSyncService.syncPriorityLevelConfiguration(clusterId,
                        (io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration) resource);
                case "ValidatingAdmissionPolicies" -> k8sResourceSyncService.syncValidatingAdmissionPolicy(clusterId,
                        (io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicy) resource);
                case "ValidatingAdmissionPolicyBindings" -> k8sResourceSyncService.syncValidatingAdmissionPolicyBinding(clusterId,
                        (io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicyBinding) resource);
                case "VolumeAttachments" -> k8sResourceSyncService.syncVolumeAttachment(clusterId,
                        (io.fabric8.kubernetes.api.model.storage.VolumeAttachment) resource);
                case "ReplicationControllers" -> k8sResourceSyncService.syncReplicationController(clusterId,
                        (io.fabric8.kubernetes.api.model.ReplicationController) resource);
                case "HPAs" -> k8sResourceSyncService.syncHpa(clusterId,
                        (io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler) resource);
                default -> log.warn("Sync not implemented for resource type: {}", resourceType);
            }
        }
    }

    @PreDestroy
    public void stopAllWatches() {
        activeWatches.values().forEach(Watch::close);
        executorService.shutdown();
    }
}
