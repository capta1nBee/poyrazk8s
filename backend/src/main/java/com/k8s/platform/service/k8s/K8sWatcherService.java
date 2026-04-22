package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Kubernetes resource watcher using Fabric8 SharedInformerFactory.
 *
 * <p>
 * Advantages over raw Watch API:
 * <ul>
 * <li>Auto-reconnect on WebSocket failures</li>
 * <li>Periodic resync (reconciliation) — configurable interval</li>
 * <li>ResourceVersion tracking — no duplicate events after restart</li>
 * <li>Shared thread pool per factory (instead of 1 thread per watch)</li>
 * <li>Type-safe onAdd/onUpdate/onDelete handlers</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class K8sWatcherService {

        private final ClusterRepository clusterRepository;
        private final ClusterContextManager clusterContextManager;
        private final K8sResourceSyncService k8sResourceSyncService;

        /**
         * One SharedInformerFactory per cluster — manages all informers for that
         * cluster.
         */
        private final Map<String, SharedInformerFactory> activeFactories = new ConcurrentHashMap<>();

        /**
         * Resync period: informers will do a full re-list every 10 minutes to catch any
         * drift.
         */
        private static final long RESYNC_PERIOD_MS = 10 * 60 * 1000L;

        // ── Public API ──────────────────────────────────────────────────────────

        public void startWatchers() {
                clusterRepository.findByIsActiveTrue().forEach(this::startWatchersForCluster);
        }

        public void startWatchersForCluster(Cluster cluster) {
                String clusterUid = cluster.getUid();

                // Stop existing informers for this cluster if any (idempotent restart)
                stopWatchersForCluster(clusterUid);

                try {
                        KubernetesClient client = clusterContextManager.getClient(clusterUid);
                        Long clusterId = cluster.getId();

                        // Reconcile cluster state before starting informers
                        try {
                                k8sResourceSyncService.reconcileCluster(clusterId, client);
                        } catch (Exception e) {
                                log.error("Failed to reconcile cluster {}: {}", cluster.getName(), e.getMessage());
                        }

                        SharedInformerFactory factory = client.informers();

                        // ── Core resources ──────────────────────────────────────
                        registerPodInformer(factory, clusterId, cluster.getName(), client);
                        registerInformer(factory, clusterId, cluster.getName(), "Deployment",
                                        io.fabric8.kubernetes.api.model.apps.Deployment.class,
                                        (c, r) -> k8sResourceSyncService.syncDeployment(c,
                                                        (io.fabric8.kubernetes.api.model.apps.Deployment) r),
                                        (uid) -> k8sResourceSyncService.markDeploymentDeleted(uid),
                                        client.apps().deployments().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "DaemonSet",
                                        io.fabric8.kubernetes.api.model.apps.DaemonSet.class,
                                        (c, r) -> k8sResourceSyncService.syncDaemonSet(c,
                                                        (io.fabric8.kubernetes.api.model.apps.DaemonSet) r),
                                        (uid) -> k8sResourceSyncService.markDaemonSetDeleted(uid),
                                        client.apps().daemonSets().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "StatefulSet",
                                        io.fabric8.kubernetes.api.model.apps.StatefulSet.class,
                                        (c, r) -> k8sResourceSyncService.syncStatefulSet(c,
                                                        (io.fabric8.kubernetes.api.model.apps.StatefulSet) r),
                                        (uid) -> k8sResourceSyncService.markStatefulSetDeleted(uid),
                                        client.apps().statefulSets().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "Service",
                                        io.fabric8.kubernetes.api.model.Service.class,
                                        (c, r) -> k8sResourceSyncService.syncService(c,
                                                        (io.fabric8.kubernetes.api.model.Service) r),
                                        (uid) -> k8sResourceSyncService.markServiceDeleted(uid),
                                        client.services().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "Ingress",
                                        io.fabric8.kubernetes.api.model.networking.v1.Ingress.class,
                                        (c, r) -> k8sResourceSyncService.syncIngress(c,
                                                        (io.fabric8.kubernetes.api.model.networking.v1.Ingress) r),
                                        (uid) -> k8sResourceSyncService.markIngressDeleted(uid),
                                        client.network().v1().ingresses().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "PVC",
                                        io.fabric8.kubernetes.api.model.PersistentVolumeClaim.class,
                                        (c, r) -> k8sResourceSyncService.syncPVC(c,
                                                        (io.fabric8.kubernetes.api.model.PersistentVolumeClaim) r),
                                        (uid) -> k8sResourceSyncService.markPVCDeleted(uid),
                                        client.persistentVolumeClaims().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "PV",
                                        io.fabric8.kubernetes.api.model.PersistentVolume.class,
                                        (c, r) -> k8sResourceSyncService.syncPersistentVolume(c,
                                                        (io.fabric8.kubernetes.api.model.PersistentVolume) r),
                                        (uid) -> k8sResourceSyncService.markPVDeleted(uid),
                                        client.persistentVolumes());
                        registerInformer(factory, clusterId, cluster.getName(), "Job",
                                        io.fabric8.kubernetes.api.model.batch.v1.Job.class,
                                        (c, r) -> k8sResourceSyncService.syncJob(c,
                                                        (io.fabric8.kubernetes.api.model.batch.v1.Job) r),
                                        (uid) -> k8sResourceSyncService.markJobDeleted(uid),
                                        client.batch().v1().jobs().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "CronJob",
                                        io.fabric8.kubernetes.api.model.batch.v1.CronJob.class,
                                        (c, r) -> k8sResourceSyncService.syncCronJob(c,
                                                        (io.fabric8.kubernetes.api.model.batch.v1.CronJob) r),
                                        (uid) -> k8sResourceSyncService.markCronJobDeleted(uid),
                                        client.batch().v1().cronjobs().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "ConfigMap",
                                        io.fabric8.kubernetes.api.model.ConfigMap.class,
                                        (c, r) -> k8sResourceSyncService.syncConfigMap(c,
                                                        (io.fabric8.kubernetes.api.model.ConfigMap) r),
                                        (uid) -> k8sResourceSyncService.markConfigMapDeleted(uid),
                                        client.configMaps().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "Secret",
                                        io.fabric8.kubernetes.api.model.Secret.class,
                                        (c, r) -> k8sResourceSyncService.syncSecret(c,
                                                        (io.fabric8.kubernetes.api.model.Secret) r),
                                        (uid) -> k8sResourceSyncService.markSecretDeleted(uid),
                                        client.secrets().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "Node",
                                        io.fabric8.kubernetes.api.model.Node.class,
                                        (c, r) -> k8sResourceSyncService.syncNode(c,
                                                        (io.fabric8.kubernetes.api.model.Node) r),
                                        (uid) -> k8sResourceSyncService.markNodeDeleted(uid),
                                        client.nodes());
                        registerInformer(factory, clusterId, cluster.getName(), "EndpointSlice",
                                        io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice.class,
                                        (c, r) -> k8sResourceSyncService.syncEndpointSlice(c,
                                                        (io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice) r),
                                        (uid) -> k8sResourceSyncService.markEndpointSliceDeleted(uid),
                                        client.discovery().v1().endpointSlices().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "Event",
                                        io.fabric8.kubernetes.api.model.Event.class,
                                        (c, r) -> k8sResourceSyncService.syncEvent(c,
                                                        (io.fabric8.kubernetes.api.model.Event) r),
                                        (uid) -> k8sResourceSyncService.markEventDeleted(uid),
                                        client.v1().events().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "Namespace",
                                        io.fabric8.kubernetes.api.model.Namespace.class,
                                        (c, r) -> k8sResourceSyncService.syncNamespace(c,
                                                        (io.fabric8.kubernetes.api.model.Namespace) r),
                                        (uid) -> k8sResourceSyncService.markNamespaceDeleted(uid),
                                        client.namespaces());
                        registerInformer(factory, clusterId, cluster.getName(), "ReplicaSet",
                                        io.fabric8.kubernetes.api.model.apps.ReplicaSet.class,
                                        (c, r) -> k8sResourceSyncService.syncReplicaSet(c,
                                                        (io.fabric8.kubernetes.api.model.apps.ReplicaSet) r),
                                        (uid) -> k8sResourceSyncService.markReplicaSetDeleted(uid),
                                        client.apps().replicaSets().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "Lease",
                                        io.fabric8.kubernetes.api.model.coordination.v1.Lease.class,
                                        (c, r) -> k8sResourceSyncService.syncLease(c,
                                                        (io.fabric8.kubernetes.api.model.coordination.v1.Lease) r),
                                        (uid) -> k8sResourceSyncService.markLeaseDeleted(uid),
                                        client.leases().inAnyNamespace());

                        // ── RBAC ────────────────────────────────────────────────
                        registerInformer(factory, clusterId, cluster.getName(), "ClusterRole",
                                        io.fabric8.kubernetes.api.model.rbac.ClusterRole.class,
                                        (c, r) -> k8sResourceSyncService.syncClusterRole(c,
                                                        (io.fabric8.kubernetes.api.model.rbac.ClusterRole) r),
                                        (uid) -> k8sResourceSyncService.markClusterRoleDeleted(uid),
                                        client.rbac().clusterRoles());
                        registerInformer(factory, clusterId, cluster.getName(), "ClusterRoleBinding",
                                        io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding.class,
                                        (c, r) -> k8sResourceSyncService.syncClusterRoleBinding(c,
                                                        (io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding) r),
                                        (uid) -> k8sResourceSyncService.markClusterRoleBindingDeleted(uid),
                                        client.rbac().clusterRoleBindings());
                        registerInformer(factory, clusterId, cluster.getName(), "Role",
                                        io.fabric8.kubernetes.api.model.rbac.Role.class,
                                        (c, r) -> k8sResourceSyncService.syncRole(c,
                                                        (io.fabric8.kubernetes.api.model.rbac.Role) r),
                                        (uid) -> k8sResourceSyncService.markRoleDeleted(uid),
                                        client.rbac().roles().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "RoleBinding",
                                        io.fabric8.kubernetes.api.model.rbac.RoleBinding.class,
                                        (c, r) -> k8sResourceSyncService.syncRoleBinding(c,
                                                        (io.fabric8.kubernetes.api.model.rbac.RoleBinding) r),
                                        (uid) -> k8sResourceSyncService.markRoleBindingDeleted(uid),
                                        client.rbac().roleBindings().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "ServiceAccount",
                                        io.fabric8.kubernetes.api.model.ServiceAccount.class,
                                        (c, r) -> k8sResourceSyncService.syncServiceAccount(c,
                                                        (io.fabric8.kubernetes.api.model.ServiceAccount) r),
                                        (uid) -> k8sResourceSyncService.markServiceAccountDeleted(uid),
                                        client.serviceAccounts().inAnyNamespace());

                        // ── Webhooks & Certificates ─────────────────────────────
                        registerInformer(factory, clusterId, cluster.getName(), "MutatingWebhookConfiguration",
                                        io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfiguration.class,
                                        (c, r) -> k8sResourceSyncService.syncMutatingWebhookConfiguration(c,
                                                        (io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfiguration) r),
                                        (uid) -> k8sResourceSyncService.markMutatingWebhookConfigurationDeleted(uid),
                                        client.admissionRegistration().v1().mutatingWebhookConfigurations());
                        registerInformer(factory, clusterId, cluster.getName(), "ValidatingWebhookConfiguration",
                                        io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration.class,
                                        (c, r) -> k8sResourceSyncService.syncValidatingWebhookConfiguration(c,
                                                        (io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration) r),
                                        (uid) -> k8sResourceSyncService.markValidatingWebhookConfigurationDeleted(uid),
                                        client.admissionRegistration().v1().validatingWebhookConfigurations());
                        registerInformer(factory, clusterId, cluster.getName(), "CertificateSigningRequest",
                                        io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest.class,
                                        (c, r) -> k8sResourceSyncService.syncCertificateSigningRequest(c,
                                                        (io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest) r),
                                        (uid) -> k8sResourceSyncService.markCertificateSigningRequestDeleted(uid),
                                        client.certificates().v1().certificateSigningRequests());

                        // ── Storage ─────────────────────────────────────────────
                        registerInformer(factory, clusterId, cluster.getName(), "CSIDriver",
                                        io.fabric8.kubernetes.api.model.storage.CSIDriver.class,
                                        (c, r) -> k8sResourceSyncService.syncCSIDriver(c,
                                                        (io.fabric8.kubernetes.api.model.storage.CSIDriver) r),
                                        (uid) -> k8sResourceSyncService.markCSIDriverDeleted(uid),
                                        client.storage().v1().csiDrivers());
                        registerInformer(factory, clusterId, cluster.getName(), "CSINode",
                                        io.fabric8.kubernetes.api.model.storage.CSINode.class,
                                        (c, r) -> k8sResourceSyncService.syncCSINode(c,
                                                        (io.fabric8.kubernetes.api.model.storage.CSINode) r),
                                        (uid) -> k8sResourceSyncService.markCSINodeDeleted(uid),
                                        client.storage().v1().csiNodes());
                        registerInformer(factory, clusterId, cluster.getName(), "VolumeAttachment",
                                        io.fabric8.kubernetes.api.model.storage.VolumeAttachment.class,
                                        (c, r) -> k8sResourceSyncService.syncVolumeAttachment(c,
                                                        (io.fabric8.kubernetes.api.model.storage.VolumeAttachment) r),
                                        (uid) -> k8sResourceSyncService.markVolumeAttachmentDeleted(uid),
                                        client.storage().v1().volumeAttachments());

                        // ── Extensions ──────────────────────────────────────────
                        registerInformer(factory, clusterId, cluster.getName(), "CustomResourceDefinition",
                                        io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition.class,
                                        (c, r) -> k8sResourceSyncService.syncCustomResourceDefinition(c,
                                                        (io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition) r),
                                        (uid) -> k8sResourceSyncService.markCustomResourceDefinitionDeleted(uid),
                                        client.apiextensions().v1().customResourceDefinitions());
                        registerInformer(factory, clusterId, cluster.getName(), "IngressClass",
                                        io.fabric8.kubernetes.api.model.networking.v1.IngressClass.class,
                                        (c, r) -> k8sResourceSyncService.syncIngressClass(c,
                                                        (io.fabric8.kubernetes.api.model.networking.v1.IngressClass) r),
                                        (uid) -> k8sResourceSyncService.markIngressClassDeleted(uid),
                                        client.network().v1().ingressClasses());
                        registerInformer(factory, clusterId, cluster.getName(), "PriorityClass",
                                        io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClass.class,
                                        (c, r) -> k8sResourceSyncService.syncPriorityClass(c,
                                                        (io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClass) r),
                                        (uid) -> k8sResourceSyncService.markPriorityClassDeleted(uid),
                                        client.scheduling().v1().priorityClasses());
                        registerInformer(factory, clusterId, cluster.getName(), "ReplicationController",
                                        io.fabric8.kubernetes.api.model.ReplicationController.class,
                                        (c, r) -> k8sResourceSyncService.syncReplicationController(c,
                                                        (io.fabric8.kubernetes.api.model.ReplicationController) r),
                                        (uid) -> k8sResourceSyncService.markReplicationControllerDeleted(uid),
                                        client.replicationControllers().inAnyNamespace());
                        registerInformer(factory, clusterId, cluster.getName(), "HPA",
                                        io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler.class,
                                        (c, r) -> k8sResourceSyncService.syncHpa(c,
                                                        (io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler) r),
                                        (uid) -> k8sResourceSyncService.markHpaDeleted(uid),
                                        client.autoscaling().v2().horizontalPodAutoscalers().inAnyNamespace());
                        registerNetworkPolicyInformer(factory, clusterId, cluster.getName(), client);

                        // ── Optional / Alpha APIs (may not be available on all clusters) ──
                        tryRegisterInformer(factory, clusterId, cluster.getName(), "IPAddress",
                                        io.fabric8.kubernetes.api.model.networking.v1alpha1.IPAddress.class,
                                        (c, r) -> k8sResourceSyncService.syncIPAddress(c,
                                                        (io.fabric8.kubernetes.api.model.networking.v1alpha1.IPAddress) r),
                                        (uid) -> k8sResourceSyncService.markIPAddressDeleted(uid),
                                        client.network().v1alpha1().ipAddresses());
                        tryRegisterInformer(factory, clusterId, cluster.getName(), "PriorityLevelConfiguration",
                                        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration.class,
                                        (c, r) -> k8sResourceSyncService.syncPriorityLevelConfiguration(c,
                                                        (io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration) r),
                                        (uid) -> k8sResourceSyncService.markPriorityLevelConfigurationDeleted(uid),
                                        client.flowControl().v1beta3().priorityLevelConfigurations());
                        tryRegisterInformer(factory, clusterId, cluster.getName(), "ValidatingAdmissionPolicy",
                                        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicy.class,
                                        (c, r) -> k8sResourceSyncService.syncValidatingAdmissionPolicy(c,
                                                        (io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicy) r),
                                        (uid) -> k8sResourceSyncService.markValidatingAdmissionPolicyDeleted(uid),
                                        client.admissionRegistration().v1beta1().validatingAdmissionPolicies());
                        tryRegisterInformer(factory, clusterId, cluster.getName(), "ValidatingAdmissionPolicyBinding",
                                        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicyBinding.class,
                                        (c, r) -> k8sResourceSyncService.syncValidatingAdmissionPolicyBinding(c,
                                                        (io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicyBinding) r),
                                        (uid) -> k8sResourceSyncService
                                                        .markValidatingAdmissionPolicyBindingDeleted(uid),
                                        client.admissionRegistration().v1beta1().validatingAdmissionPolicyBindings());

                        factory.startAllRegisteredInformers();
                        activeFactories.put(clusterUid, factory);
                        log.info("Started SharedInformerFactory for cluster '{}' with resync={}ms", cluster.getName(),
                                        RESYNC_PERIOD_MS);

                } catch (Exception e) {
                        log.error("Failed to start informers for cluster {}: {}", cluster.getName(), e.getMessage(), e);
                }
        }

        // ── Functional interfaces for sync/delete callbacks ────────────────────

        @FunctionalInterface
        private interface SyncCallback {
                void sync(Long clusterId, HasMetadata resource);
        }

        @FunctionalInterface
        private interface DeleteCallback {
                void delete(String uid);
        }

        // ── Generic informer registration ───────────────────────────────────────

        private <T extends HasMetadata> void registerInformer(
                        SharedInformerFactory factory, Long clusterId, String clusterName,
                        String resourceType, Class<T> clazz,
                        SyncCallback onSync, DeleteCallback onDelete,
                        io.fabric8.kubernetes.client.dsl.Informable<T> informable) {

                informable.inform(new ResourceEventHandler<T>() {
                        @Override
                        public void onAdd(T obj) {
                                try {
                                        log.info("Cluster {}: [ADD] {} {}", clusterName, resourceType,
                                                        obj.getMetadata().getName());
                                        onSync.sync(clusterId, obj);
                                } catch (Exception e) {
                                        log.error("Failed to sync [ADD] {} {} for cluster {}: {}",
                                                        resourceType, obj.getMetadata().getName(), clusterName,
                                                        e.getMessage());
                                }
                        }

                        @Override
                        public void onUpdate(T oldObj, T newObj) {
                                // Skip if resourceVersion hasn't changed (no real update)
                                if (oldObj.getMetadata().getResourceVersion()
                                                .equals(newObj.getMetadata().getResourceVersion())) {
                                        return;
                                }
                                try {
                                        log.info("Cluster {}: [UPDATE] {} {}", clusterName, resourceType,
                                                        newObj.getMetadata().getName());
                                        onSync.sync(clusterId, newObj);
                                } catch (Exception e) {
                                        log.error("Failed to sync [UPDATE] {} {} for cluster {}: {}",
                                                        resourceType, newObj.getMetadata().getName(), clusterName,
                                                        e.getMessage());
                                }
                        }

                        @Override
                        public void onDelete(T obj, boolean deletedFinalStateUnknown) {
                                try {
                                        log.info("Cluster {}: [DELETE] {} {}", clusterName, resourceType,
                                                        obj.getMetadata().getName());
                                        onDelete.delete(obj.getMetadata().getUid());
                                } catch (Exception e) {
                                        log.error("Failed to sync [DELETE] {} {} for cluster {}: {}",
                                                        resourceType, obj.getMetadata().getName(), clusterName,
                                                        e.getMessage());
                                }
                        }
                }, RESYNC_PERIOD_MS);

                log.info("Registered {} informer for cluster '{}'", resourceType, clusterName);
        }

        /**
         * Register informer for optional/alpha APIs — logs a warning if not available.
         */
        private <T extends HasMetadata> void tryRegisterInformer(
                        SharedInformerFactory factory, Long clusterId, String clusterName,
                        String resourceType, Class<T> clazz,
                        SyncCallback onSync, DeleteCallback onDelete,
                        io.fabric8.kubernetes.client.dsl.Informable<T> informable) {
                try {
                        registerInformer(factory, clusterId, clusterName, resourceType, clazz, onSync, onDelete,
                                        informable);
                } catch (Exception e) {
                        log.warn("{} API not available for cluster {}: {}", resourceType, clusterName, e.getMessage());
                }
        }

        /**
         * Pod informer — same as generic but kept separate for clarity.
         */
        private void registerPodInformer(SharedInformerFactory factory, Long clusterId, String clusterName,
                        KubernetesClient client) {
                registerInformer(factory, clusterId, clusterName, "Pod",
                                io.fabric8.kubernetes.api.model.Pod.class,
                                (c, r) -> k8sResourceSyncService.syncPod(c, (io.fabric8.kubernetes.api.model.Pod) r),
                                (uid) -> k8sResourceSyncService.markPodDeleted(uid),
                                client.pods().inAnyNamespace());
        }

        /**
         * NetworkPolicy informer — special delete handler needs clusterId + namespace +
         * name.
         */
        private void registerNetworkPolicyInformer(SharedInformerFactory factory, Long clusterId, String clusterName,
                        KubernetesClient client) {
                client.network().v1().networkPolicies().inAnyNamespace().inform(
                                new ResourceEventHandler<io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy>() {
                                        @Override
                                        public void onAdd(
                                                        io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy obj) {
                                                // NetworkPolicy sync is handled via generated network policies — no
                                                // generic sync needed
                                                log.debug("Cluster {}: [ADD] NetworkPolicy {}", clusterName,
                                                                obj.getMetadata().getName());
                                        }

                                        @Override
                                        public void onUpdate(
                                                        io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy oldObj,
                                                        io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy newObj) {
                                                if (oldObj.getMetadata().getResourceVersion()
                                                                .equals(newObj.getMetadata().getResourceVersion())) {
                                                        return;
                                                }
                                                log.debug("Cluster {}: [UPDATE] NetworkPolicy {}", clusterName,
                                                                newObj.getMetadata().getName());
                                        }

                                        @Override
                                        public void onDelete(
                                                        io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy obj,
                                                        boolean deletedFinalStateUnknown) {
                                                try {
                                                        log.debug("Cluster {}: [DELETE] NetworkPolicy {}", clusterName,
                                                                        obj.getMetadata().getName());
                                                        k8sResourceSyncService.markGeneratedNetworkPolicyDeleted(
                                                                        clusterId, obj.getMetadata().getNamespace(),
                                                                        obj.getMetadata().getName());
                                                } catch (Exception e) {
                                                        log.error("Failed to sync [DELETE] NetworkPolicy {} for cluster {}: {}",
                                                                        obj.getMetadata().getName(), clusterName,
                                                                        e.getMessage());
                                                }
                                        }
                                }, RESYNC_PERIOD_MS);

                log.info("Registered NetworkPolicy informer for cluster '{}'", clusterName);
        }

        // ── Lifecycle ───────────────────────────────────────────────────────────

        public void stopWatchersForCluster(String clusterUid) {
                SharedInformerFactory factory = activeFactories.remove(clusterUid);
                if (factory != null) {
                        try {
                                factory.stopAllRegisteredInformers();
                                log.info("Stopped informer factory for cluster {}", clusterUid);
                        } catch (Exception e) {
                                log.warn("Error closing informer factory for cluster {}: {}", clusterUid,
                                                e.getMessage());
                        }
                }
        }

        @PreDestroy
        public void stopAllWatches() {
                log.info("Shutting down all informer factories ({} clusters)", activeFactories.size());
                activeFactories.forEach((uid, factory) -> {
                        try {
                                factory.stopAllRegisteredInformers();
                        } catch (Exception e) {
                                log.warn("Error closing informer factory for cluster {}: {}", uid, e.getMessage());
                        }
                });
                activeFactories.clear();
                log.info("All informer factories closed");
        }
}
