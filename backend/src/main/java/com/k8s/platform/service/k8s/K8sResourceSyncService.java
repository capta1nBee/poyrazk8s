package com.k8s.platform.service.k8s;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.k8s.*;
import com.k8s.platform.domain.entity.k8s.BaseK8sEntity;
import com.k8s.platform.domain.repository.k8s.*;
import io.fabric8.kubernetes.api.model.NodeAddress;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class K8sResourceSyncService {

    private final PodRepository podRepository;
    private final ServiceRepository serviceRepository;
    private final DaemonSetRepository daemonSetRepository;
    private final SecretRepository secretRepository;
    private final ConfigMapRepository configMapRepository;
    private final StatefulSetRepository statefulSetRepository;
    private final K8sNodeRepository nodeRepository;
    private final IngressRepository ingressRepository;
    private final JobRepository jobRepository;
    private final CronJobRepository cronJobRepository;
    private final DeploymentRepository deploymentRepository;
    private final EndpointSliceRepository endpointSliceRepository;
    private final K8sEventRepository eventRepository;
    private final LeaseRepository leaseRepository;
    private final K8sNamespaceRepository namespaceRepository;
    private final PersistentVolumeClaimRepository pvcRepository;
    private final PersistentVolumeRepository pvRepository;
    private final ReplicaSetRepository replicaSetRepository;

    // RBAC
    private final ClusterRoleBindingRepository clusterRoleBindingRepository;
    private final ClusterRoleRepository clusterRoleRepository;
    private final K8sRoleRepository k8sRoleRepository;
    private final RoleBindingRepository roleBindingRepository;
    private final ServiceAccountRepository serviceAccountRepository;

    // Administrator
    private final MutatingWebhookConfigurationRepository mutatingWebhookConfigurationRepository;
    private final ValidatingWebhookConfigurationRepository validatingWebhookConfigurationRepository;

    // Other Resources
    private final ApplicationRepository applicationRepository;
    private final CertificateSigningRequestRepository certificateSigningRequestRepository;
    private final CSIDriverRepository csiDriverRepository;
    private final CSINodeRepository csiNodeRepository;
    private final CustomResourceDefinitionRepository customResourceDefinitionRepository;
    private final IngressClassRepository ingressClassRepository;
    private final IPAddressRepository ipAddressRepository;
    private final PriorityClassRepository priorityClassRepository;
    private final PriorityLevelConfigurationRepository priorityLevelConfigurationRepository;

    private final ValidatingAdmissionPolicyRepository validatingAdmissionPolicyRepository;
    private final ValidatingAdmissionPolicyBindingRepository validatingAdmissionPolicyBindingRepository;
    private final VolumeAttachmentRepository volumeAttachmentRepository;

    // Workloads
    private final ReplicationControllerRepository replicationControllerRepository;

    // Autoscaling
    private final HpaRepository hpaRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean ensureNamespace(ObjectMeta meta, String kind, String name) {
        if (meta == null) {
            log.warn("Skipping {} {}: metadata is null", kind, name);
            return false;
        }
        if (meta.getNamespace() == null) {
            log.warn("Skipping {} {}: namespace is null", kind, name);
            return false;
        }
        return true;
    }

    // Retry logic for DB operations with exponential backoff
    private void retryableDbOperation(Runnable operation, String operationName) {
        int maxRetries = 3;
        long initialWaitMs = 100;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                operation.run();
                return; // Success
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    log.error("Failed to {} after {} attempts: {}", operationName, maxRetries, e.getMessage());
                    return;
                }
                long waitMs = initialWaitMs * (long) Math.pow(2, attempt - 1);
                log.warn("Failed to {} (attempt {}/{}), retrying in {}ms: {}",
                        operationName, attempt, maxRetries, waitMs, e.getMessage());
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Retry sleep interrupted");
                    return;
                }
            }
        }
    }

    @Transactional
    public void syncPod(Long clusterId, io.fabric8.kubernetes.api.model.Pod pod) {
        if (!ensureNamespace(pod.getMetadata(), "Pod",
                pod.getMetadata() != null ? pod.getMetadata().getName() : "<unknown>"))
            return;
        String uid = pod.getMetadata().getUid();
        Pod entity = podRepository.findByUid(uid)
                .orElseGet(() -> {
                    Pod newPod = new Pod();
                    newPod.setUid(uid);
                    return newPod;
                });
        entity.setK8sCreatedAt(pod.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(pod.getMetadata().getNamespace());
        entity.setName(pod.getMetadata().getName());
        entity.setApiVersion(pod.getApiVersion());
        entity.setResourceVersion(pod.getMetadata().getResourceVersion());
        entity.setGeneration(
                pod.getMetadata().getGeneration() != null ? pod.getMetadata().getGeneration().intValue() : null);
        entity.setPhase(pod.getStatus().getPhase());
        entity.setNodeName(pod.getSpec().getNodeName());

        // Add restart count from status
        int restartCount = 0;
        if (pod.getStatus().getContainerStatuses() != null) {
            for (var containerStatus : pod.getStatus().getContainerStatuses()) {
                restartCount += containerStatus.getRestartCount();
            }
        }
        entity.setRestartCount(restartCount);

        // Add QoS class
        if (pod.getStatus() != null && pod.getStatus().getQosClass() != null) {
            entity.setQosClass(pod.getStatus().getQosClass());
        }

        // Add containers info
        entity.setContainers(serialize(pod.getSpec().getContainers()));
        entity.setInitContainers(serialize(pod.getSpec().getInitContainers()));

        // Add conditions
        entity.setConditions(serialize(pod.getStatus().getConditions()));

        entity.setOwnerRefs(serialize(pod.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(pod.getMetadata().getLabels()));
        entity.setAnnotations(serialize(pod.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(pod.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        podRepository.save(entity);
    }

    @Transactional
    public void markPodDeleted(String uid) {
        podRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            podRepository.save(entity);
            log.info("Successfully marked Pod {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark Pod {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncService(Long clusterId, io.fabric8.kubernetes.api.model.Service service) {
        if (!ensureNamespace(service.getMetadata(), "Service",
                service.getMetadata() != null ? service.getMetadata().getName() : "<unknown>"))
            return;
        String uid = service.getMetadata().getUid();
        com.k8s.platform.domain.entity.k8s.Service entity = serviceRepository.findByUid(uid)
                .orElseGet(() -> {
                    com.k8s.platform.domain.entity.k8s.Service newService = new com.k8s.platform.domain.entity.k8s.Service();
                    newService.setUid(uid);
                    return newService;
                });
        entity.setK8sCreatedAt(service.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(service.getMetadata().getNamespace());
        entity.setName(service.getMetadata().getName());
        entity.setApiVersion(service.getApiVersion());
        entity.setResourceVersion(service.getMetadata().getResourceVersion());
        entity.setGeneration(
                service.getMetadata().getGeneration() != null ? service.getMetadata().getGeneration().intValue()
                        : null);
        entity.setServiceType(service.getSpec().getType());
        entity.setClusterIP(service.getSpec().getClusterIP());
        entity.setExternalIPs(serialize(service.getSpec().getExternalIPs()));
        entity.setPorts(serialize(service.getSpec().getPorts()));
        entity.setSelector(serialize(service.getSpec().getSelector()));
        entity.setOwnerRefs(serialize(service.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(service.getMetadata().getLabels()));
        entity.setAnnotations(serialize(service.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(service.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        serviceRepository.save(entity);
    }

    @Transactional
    public void markServiceDeleted(String uid) {
        serviceRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            serviceRepository.save(entity);
            log.info("Successfully marked Service {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark Service {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncDaemonSet(Long clusterId, io.fabric8.kubernetes.api.model.apps.DaemonSet ds) {
        if (!ensureNamespace(ds.getMetadata(), "DaemonSet",
                ds.getMetadata() != null ? ds.getMetadata().getName() : "<unknown>"))
            return;
        String uid = ds.getMetadata().getUid();
        DaemonSet entity = daemonSetRepository.findByUid(uid)
                .orElseGet(() -> {
                    DaemonSet newDs = new DaemonSet();
                    newDs.setUid(uid);
                    return newDs;
                });

        entity.setClusterId(clusterId);
        entity.setNamespace(ds.getMetadata().getNamespace());
        entity.setName(ds.getMetadata().getName());
        entity.setApiVersion(ds.getApiVersion());
        entity.setResourceVersion(ds.getMetadata().getResourceVersion());
        entity.setGeneration(
                ds.getMetadata().getGeneration() != null ? ds.getMetadata().getGeneration().intValue() : null);
        entity.setDesiredNumberScheduled(ds.getStatus().getDesiredNumberScheduled());
        entity.setCurrentNumberScheduled(ds.getStatus().getCurrentNumberScheduled());
        entity.setNumberReady(ds.getStatus().getNumberReady());
        entity.setNumberAvailable(ds.getStatus().getNumberAvailable());
        entity.setNodeSelector(serialize(ds.getSpec().getTemplate().getSpec().getNodeSelector()));
        entity.setUpdateStrategy(serialize(ds.getSpec().getUpdateStrategy()));
        entity.setOwnerRefs(serialize(ds.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(ds.getMetadata().getLabels()));
        entity.setAnnotations(serialize(ds.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(ds.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setK8sCreatedAt(ds.getMetadata().getCreationTimestamp());

        daemonSetRepository.save(entity);
    }

    @Transactional
    public void markDaemonSetDeleted(String uid) {
        daemonSetRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            daemonSetRepository.save(entity);
            log.info("Successfully marked DaemonSet {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark DaemonSet {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncStatefulSet(Long clusterId, io.fabric8.kubernetes.api.model.apps.StatefulSet ss) {
        if (!ensureNamespace(ss.getMetadata(), "StatefulSet",
                ss.getMetadata() != null ? ss.getMetadata().getName() : "<unknown>"))
            return;
        String uid = ss.getMetadata().getUid();
        StatefulSet entity = statefulSetRepository.findByUid(uid)
                .orElseGet(() -> {
                    StatefulSet newSs = new StatefulSet();
                    newSs.setUid(uid);
                    return newSs;
                });
        entity.setK8sCreatedAt(ss.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(ss.getMetadata().getNamespace());
        entity.setName(ss.getMetadata().getName());
        entity.setApiVersion(ss.getApiVersion());
        entity.setResourceVersion(ss.getMetadata().getResourceVersion());
        entity.setGeneration(
                ss.getMetadata().getGeneration() != null ? ss.getMetadata().getGeneration().intValue() : null);
        entity.setReplicas(ss.getSpec().getReplicas());
        entity.setReadyReplicas(ss.getStatus().getReadyReplicas());
        entity.setCurrentReplicas(ss.getStatus().getCurrentReplicas());
        entity.setUpdatedReplicas(ss.getStatus().getUpdatedReplicas());
        entity.setServiceName(ss.getSpec().getServiceName());
        entity.setUpdateStrategy(serialize(ss.getSpec().getUpdateStrategy()));
        entity.setOwnerRefs(serialize(ss.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(ss.getMetadata().getLabels()));
        entity.setAnnotations(serialize(ss.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(ss.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        statefulSetRepository.save(entity);
    }

    @Transactional
    public void markStatefulSetDeleted(String uid) {
        statefulSetRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            statefulSetRepository.save(entity);
            log.info("Successfully marked StatefulSet {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark StatefulSet {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncSecret(Long clusterId, io.fabric8.kubernetes.api.model.Secret secret) {
        if (!ensureNamespace(secret.getMetadata(), "Secret",
                secret.getMetadata() != null ? secret.getMetadata().getName() : "<unknown>"))
            return;
        String uid = secret.getMetadata().getUid();
        com.k8s.platform.domain.entity.k8s.Secret entity = secretRepository.findByUid(uid)
                .orElseGet(() -> {
                    com.k8s.platform.domain.entity.k8s.Secret newSecret = new com.k8s.platform.domain.entity.k8s.Secret();
                    newSecret.setUid(uid);
                    return newSecret;
                });
        entity.setK8sCreatedAt(secret.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(secret.getMetadata().getNamespace());
        entity.setName(secret.getMetadata().getName());
        entity.setApiVersion(secret.getApiVersion());
        entity.setResourceVersion(secret.getMetadata().getResourceVersion());
        entity.setSecretType(secret.getType());
        entity.setDataCount(secret.getData() != null ? secret.getData().size() : 0);
        entity.setOwnerRefs(serialize(secret.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(secret.getMetadata().getLabels()));
        entity.setAnnotations(serialize(secret.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(secret.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        secretRepository.save(entity);
    }

    @Transactional
    public void markSecretDeleted(String uid) {
        secretRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            secretRepository.save(entity);
            log.info("Successfully marked Secret {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark Secret {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncConfigMap(Long clusterId, io.fabric8.kubernetes.api.model.ConfigMap cm) {
        if (!ensureNamespace(cm.getMetadata(), "ConfigMap",
                cm.getMetadata() != null ? cm.getMetadata().getName() : "<unknown>"))
            return;
        String uid = cm.getMetadata().getUid();
        com.k8s.platform.domain.entity.k8s.ConfigMap entity = configMapRepository.findByUid(uid)
                .orElseGet(() -> {
                    com.k8s.platform.domain.entity.k8s.ConfigMap newCm = new com.k8s.platform.domain.entity.k8s.ConfigMap();
                    newCm.setUid(uid);
                    return newCm;
                });
        entity.setK8sCreatedAt(cm.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(cm.getMetadata().getNamespace());
        entity.setName(cm.getMetadata().getName());
        entity.setApiVersion(cm.getApiVersion());
        entity.setResourceVersion(cm.getMetadata().getResourceVersion());
        entity.setDataCount(cm.getData() != null ? cm.getData().size() : 0);
        entity.setImmutable(cm.getImmutable() != null ? cm.getImmutable() : false);
        entity.setOwnerRefs(serialize(cm.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(cm.getMetadata().getLabels()));
        entity.setAnnotations(serialize(cm.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(cm.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        configMapRepository.save(entity);
    }

    @Transactional
    public void markConfigMapDeleted(String uid) {
        configMapRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            configMapRepository.save(entity);
            log.info("Successfully marked ConfigMap {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark ConfigMap {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncNode(Long clusterId, io.fabric8.kubernetes.api.model.Node node) {
        String uid = node.getMetadata().getUid();
        K8sNode entity = nodeRepository.findByUid(uid)
                .orElseGet(() -> {
                    K8sNode newNode = new K8sNode();
                    newNode.setUid(uid);
                    return newNode;
                });
        entity.setK8sCreatedAt(node.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setName(node.getMetadata().getName());
        entity.setApiVersion(node.getApiVersion());
        entity.setResourceVersion(node.getMetadata().getResourceVersion());

        // Status
        String baseStatus = node.getStatus().getConditions().stream()
                .filter(c -> "Ready".equals(c.getType()))
                .map(c -> "True".equals(c.getStatus()) ? "Ready" : "NotReady")
                .findFirst().orElse("Unknown");

        if (node.getSpec() != null && Boolean.TRUE.equals(node.getSpec().getUnschedulable())) {
            entity.setStatus(baseStatus + ",SchedulingDisabled");
            entity.setUnschedulable(true);
        } else {
            entity.setStatus(baseStatus);
            entity.setUnschedulable(false);
        }

        // Roles - extract from labels
        String roles = extractNodeRoles(node.getMetadata().getLabels());
        entity.setRoles(roles);

        // Version info
        entity.setVersion(node.getStatus().getNodeInfo().getKubeletVersion());
        entity.setKubeletVersion(node.getStatus().getNodeInfo().getKubeletVersion());

        // OS and Kernel
        entity.setOs(node.getStatus().getNodeInfo().getOperatingSystem());
        entity.setKernel(node.getStatus().getNodeInfo().getKernelVersion());

        // IP Address
        entity.setNodeIP(node.getStatus().getAddresses().stream()
                .filter(a -> "InternalIP".equals(a.getType()))
                .map(NodeAddress::getAddress)
                .findFirst().orElse(null));

        // Capacity and Allocatable
        var capacity = node.getStatus().getCapacity();
        var allocatable = node.getStatus().getAllocatable();

        if (capacity != null) {
            entity.setCpuCapacity(capacity.get("cpu") != null ? capacity.get("cpu").toString() : null);
            entity.setMemoryCapacity(capacity.get("memory") != null ? capacity.get("memory").toString() : null);
        }

        if (allocatable != null) {
            entity.setAllocatableCpu(allocatable.get("cpu") != null ? allocatable.get("cpu").toString() : null);
            entity.setAllocatableMemory(
                    allocatable.get("memory") != null ? allocatable.get("memory").toString() : null);
        }

        entity.setCapacity(serialize(capacity));
        entity.setAllocatable(serialize(allocatable));
        entity.setOwnerRefs(serialize(node.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(node.getMetadata().getLabels()));
        entity.setAnnotations(serialize(node.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(node.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        nodeRepository.save(entity);
    }

    @Transactional
    public void markNodeDeleted(String uid) {
        nodeRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            nodeRepository.save(entity);
            log.info("Successfully marked Node {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark Node {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncIngress(Long clusterId, io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress) {
        if (!ensureNamespace(ingress.getMetadata(), "Ingress",
                ingress.getMetadata() != null ? ingress.getMetadata().getName() : "<unknown>"))
            return;
        String uid = ingress.getMetadata().getUid();
        Ingress entity = ingressRepository.findByUid(uid)
                .orElseGet(() -> {
                    Ingress newIngress = new Ingress();
                    newIngress.setUid(uid);
                    return newIngress;
                });
        entity.setK8sCreatedAt(ingress.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(ingress.getMetadata().getNamespace());
        entity.setName(ingress.getMetadata().getName());
        entity.setApiVersion(ingress.getApiVersion());
        entity.setResourceVersion(ingress.getMetadata().getResourceVersion());
        entity.setGeneration(
                ingress.getMetadata().getGeneration() != null ? ingress.getMetadata().getGeneration().intValue()
                        : null);
        entity.setIngressClassName(ingress.getSpec().getIngressClassName());
        entity.setRules(serialize(ingress.getSpec().getRules()));
        entity.setTls(serialize(ingress.getSpec().getTls()));
        if (ingress.getStatus() != null && ingress.getStatus().getLoadBalancer() != null &&
                ingress.getStatus().getLoadBalancer().getIngress() != null &&
                !ingress.getStatus().getLoadBalancer().getIngress().isEmpty()) {
            entity.setLoadBalancerIP(ingress.getStatus().getLoadBalancer().getIngress().get(0).getIp());
        }
        entity.setOwnerRefs(serialize(ingress.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(ingress.getMetadata().getLabels()));
        entity.setAnnotations(serialize(ingress.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(ingress.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        ingressRepository.save(entity);
    }

    @Transactional
    public void markIngressDeleted(String uid) {
        ingressRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            ingressRepository.save(entity);
            log.info("Successfully marked Ingress {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark Ingress {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncCronJob(Long clusterId, io.fabric8.kubernetes.api.model.batch.v1.CronJob cj) {
        if (!ensureNamespace(cj.getMetadata(), "CronJob",
                cj.getMetadata() != null ? cj.getMetadata().getName() : "<unknown>"))
            return;
        String uid = cj.getMetadata().getUid();
        CronJob entity = cronJobRepository.findByUid(uid)
                .orElseGet(() -> {
                    CronJob newCronJob = new CronJob();
                    newCronJob.setUid(uid);
                    return newCronJob;
                });
        entity.setK8sCreatedAt(cj.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(cj.getMetadata().getNamespace());
        entity.setName(cj.getMetadata().getName());
        entity.setApiVersion(cj.getApiVersion());
        entity.setResourceVersion(cj.getMetadata().getResourceVersion());
        entity.setGeneration(
                cj.getMetadata().getGeneration() != null ? cj.getMetadata().getGeneration().intValue() : null);
        entity.setSchedule(cj.getSpec().getSchedule());
        entity.setConcurrencyPolicy(cj.getSpec().getConcurrencyPolicy());
        entity.setSuspend(cj.getSpec().getSuspend());
        entity.setLastScheduleTime(parseTimestamp(cj.getStatus().getLastScheduleTime()));
        entity.setOwnerRefs(serialize(cj.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(cj.getMetadata().getLabels()));
        entity.setAnnotations(serialize(cj.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(cj.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        cronJobRepository.save(entity);
    }

    @Transactional
    public void markCronJobDeleted(String uid) {
        cronJobRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            cronJobRepository.save(entity);
            log.info("Successfully marked CronJob {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark CronJob {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncJob(Long clusterId, io.fabric8.kubernetes.api.model.batch.v1.Job job) {
        if (!ensureNamespace(job.getMetadata(), "Job",
                job.getMetadata() != null ? job.getMetadata().getName() : "<unknown>"))
            return;
        String uid = job.getMetadata().getUid();
        Job entity = jobRepository.findByUid(uid)
                .orElseGet(() -> {
                    Job newJob = new Job();
                    newJob.setUid(uid);
                    return newJob;
                });
        entity.setK8sCreatedAt(job.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(job.getMetadata().getNamespace());
        entity.setName(job.getMetadata().getName());
        entity.setApiVersion(job.getApiVersion());
        entity.setResourceVersion(job.getMetadata().getResourceVersion());
        entity.setGeneration(
                job.getMetadata().getGeneration() != null ? job.getMetadata().getGeneration().intValue() : null);
        entity.setCompletions(job.getSpec().getCompletions());
        entity.setParallelism(job.getSpec().getParallelism());

        if (job.getStatus() != null) {
            entity.setActive(job.getStatus().getActive());
            entity.setSucceeded(job.getStatus().getSucceeded());
            entity.setFailed(job.getStatus().getFailed());
            entity.setStartTime(parseTimestamp(job.getStatus().getStartTime()));
            entity.setCompletionTime(parseTimestamp(job.getStatus().getCompletionTime()));

            // Determine status
            String status = "Running";
            if (job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded() > 0) {
                status = "Succeeded";
            } else if (job.getStatus().getFailed() != null && job.getStatus().getFailed() > 0) {
                status = "Failed";
            }
            entity.setStatus(status);
        }

        entity.setOwnerRefs(serialize(job.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(job.getMetadata().getLabels()));
        entity.setAnnotations(serialize(job.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(job.getMetadata().getManagedFields()));
        entity.setK8sCreatedAt(job.getMetadata().getCreationTimestamp());
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        jobRepository.save(entity);
    }

    @Transactional
    public void markJobDeleted(String uid) {
        jobRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            jobRepository.save(entity);
            log.info("Successfully marked Job {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark Job {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncDeployment(Long clusterId, io.fabric8.kubernetes.api.model.apps.Deployment deployment) {
        if (!ensureNamespace(deployment.getMetadata(), "Deployment",
                deployment.getMetadata() != null ? deployment.getMetadata().getName() : "<unknown>"))
            return;
        String uid = deployment.getMetadata().getUid();
        Deployment entity = deploymentRepository.findByUid(uid)
                .orElseGet(() -> {
                    Deployment newDeployment = new Deployment();
                    newDeployment.setUid(uid);
                    return newDeployment;
                });
        entity.setK8sCreatedAt(deployment.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(deployment.getMetadata().getNamespace());
        entity.setName(deployment.getMetadata().getName());
        entity.setApiVersion(deployment.getApiVersion());
        entity.setResourceVersion(deployment.getMetadata().getResourceVersion());
        entity.setGeneration(
                deployment.getMetadata().getGeneration() != null ? deployment.getMetadata().getGeneration().intValue()
                        : null);
        entity.setReplicas(deployment.getSpec().getReplicas());
        entity.setAvailableReplicas(deployment.getStatus().getAvailableReplicas());
        entity.setDesiredReplicas(deployment.getStatus().getReplicas());
        entity.setReadyReplicas(deployment.getStatus().getReadyReplicas());
        entity.setUpdatedReplicas(deployment.getStatus().getUpdatedReplicas());

        // Strategy type
        if (deployment.getSpec().getStrategy() != null && deployment.getSpec().getStrategy().getType() != null) {
            entity.setStrategyType(deployment.getSpec().getStrategy().getType());
        }

        // Paused status
        entity.setPaused(deployment.getSpec().getPaused() != null ? deployment.getSpec().getPaused() : false);

        // Add containers info
        entity.setContainers(serialize(deployment.getSpec().getTemplate().getSpec().getContainers()));

        entity.setStrategy(serialize(deployment.getSpec().getStrategy()));
        entity.setOwnerRefs(serialize(deployment.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(deployment.getMetadata().getLabels()));
        entity.setAnnotations(serialize(deployment.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(deployment.getMetadata().getManagedFields()));
        entity.setK8sCreatedAt(deployment.getMetadata().getCreationTimestamp());
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        deploymentRepository.save(entity);
    }

    @Transactional
    public void markDeploymentDeleted(String uid) {
        deploymentRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            deploymentRepository.save(entity);
            log.info("Successfully marked Deployment {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark Deployment {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncEndpointSlice(Long clusterId, io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice es) {
        if (!ensureNamespace(es.getMetadata(), "EndpointSlice",
                es.getMetadata() != null ? es.getMetadata().getName() : "<unknown>"))
            return;
        String uid = es.getMetadata().getUid();
        EndpointSlice entity = endpointSliceRepository.findByUid(uid)
                .orElseGet(() -> {
                    EndpointSlice newEs = new EndpointSlice();
                    newEs.setUid(uid);
                    return newEs;
                });
        entity.setK8sCreatedAt(es.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(es.getMetadata().getNamespace());
        entity.setName(es.getMetadata().getName());
        entity.setApiVersion(es.getApiVersion());
        entity.setResourceVersion(es.getMetadata().getResourceVersion());
        entity.setAddressType(es.getAddressType());
        entity.setEndpoints(serialize(es.getEndpoints()));
        entity.setPorts(serialize(es.getPorts()));
        entity.setOwnerRefs(serialize(es.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(es.getMetadata().getLabels()));
        entity.setAnnotations(serialize(es.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(es.getMetadata().getManagedFields()));
        entity.setK8sCreatedAt(es.getMetadata().getCreationTimestamp());
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        endpointSliceRepository.save(entity);
    }

    @Transactional
    public void markEndpointSliceDeleted(String uid) {
        endpointSliceRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            endpointSliceRepository.save(entity);
            log.info("Successfully marked EndpointSlice {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark EndpointSlice {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncEvent(Long clusterId, io.fabric8.kubernetes.api.model.Event event) {
        retryableDbOperation(() -> {
            if (!ensureNamespace(event.getMetadata(), "Event",
                    event.getMetadata() != null ? event.getMetadata().getName() : "<unknown>"))
                return;
            String uid = event.getMetadata().getUid();
            K8sEvent entity = eventRepository.findByUid(uid)
                    .orElseGet(() -> {
                        K8sEvent newEvent = new K8sEvent();
                        newEvent.setUid(uid);
                        return newEvent;
                    });
            entity.setK8sCreatedAt(event.getMetadata().getCreationTimestamp());
            entity.setClusterId(clusterId);
            entity.setNamespace(event.getMetadata().getNamespace());
            entity.setName(event.getMetadata().getName());
            entity.setApiVersion(event.getApiVersion());
            entity.setResourceVersion(event.getMetadata().getResourceVersion());
            entity.setReason(event.getReason());
            entity.setMessage(event.getMessage());
            entity.setType(event.getType());
            entity.setCount(event.getCount());
            entity.setFirstTimestamp(parseTimestamp(event.getFirstTimestamp()));
            entity.setLastTimestamp(parseTimestamp(event.getLastTimestamp()));
            entity.setInvolvedObject(serialize(event.getInvolvedObject()));
            entity.setSource(serialize(event.getSource()));
            entity.setManagedFields(serialize(event.getMetadata().getManagedFields()));
            entity.setK8sCreatedAt(event.getMetadata().getCreationTimestamp());
            entity.setIsDeleted(false);
            entity.setUpdatedAt(LocalDateTime.now());

            eventRepository.save(entity);
        }, "syncEvent");
    }

    @Transactional
    public void markEventDeleted(String uid) {
        retryableDbOperation(() -> {
            eventRepository.findByUid(uid).ifPresentOrElse(entity -> {
                entity.setIsDeleted(true);
                entity.setDeletedAt(LocalDateTime.now());
                eventRepository.save(entity);
                log.info("Successfully marked Event {} as deleted in DB", uid);
            }, () -> log.warn("Failed to mark Event {} as deleted: not found in DB", uid));
        }, "markEventDeleted");
    }

    @Transactional
    public void syncLease(Long clusterId, io.fabric8.kubernetes.api.model.coordination.v1.Lease lease) {
        if (!ensureNamespace(lease.getMetadata(), "Lease",
                lease.getMetadata() != null ? lease.getMetadata().getName() : "<unknown>"))
            return;
        String uid = lease.getMetadata().getUid();
        Lease entity = leaseRepository.findByUid(uid)
                .orElseGet(() -> {
                    Lease newLease = new Lease();
                    newLease.setUid(uid);
                    return newLease;
                });
        entity.setK8sCreatedAt(lease.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(lease.getMetadata().getNamespace());
        entity.setName(lease.getMetadata().getName());
        entity.setApiVersion(lease.getApiVersion());
        entity.setResourceVersion(lease.getMetadata().getResourceVersion());
        if (lease.getSpec() != null) {
            entity.setHolderIdentity(lease.getSpec().getHolderIdentity());
            entity.setLeaseDurationSeconds(lease.getSpec().getLeaseDurationSeconds());
            if (lease.getSpec().getAcquireTime() != null) {
                entity.setAcquireTime(
                        lease.getSpec().getAcquireTime().toLocalDateTime());
            }
            if (lease.getSpec().getRenewTime() != null) {
                entity.setRenewTime(
                        lease.getSpec().getRenewTime().toLocalDateTime());
            }
        }
        entity.setOwnerRefs(serialize(lease.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(lease.getMetadata().getLabels()));
        entity.setAnnotations(serialize(lease.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(lease.getMetadata().getManagedFields()));
        entity.setK8sCreatedAt(lease.getMetadata().getCreationTimestamp());
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        leaseRepository.save(entity);
    }

    @Transactional
    public void markLeaseDeleted(String uid) {
        leaseRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            leaseRepository.save(entity);
            log.info("Successfully marked Lease {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark Lease {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncPV(Long clusterId, io.fabric8.kubernetes.api.model.PersistentVolume pv) {
        String uid = pv.getMetadata().getUid();
        PersistentVolume entity = pvRepository.findByUid(uid)
                .orElseGet(() -> {
                    PersistentVolume newPv = new PersistentVolume();
                    newPv.setUid(uid);
                    return newPv;
                });
        entity.setK8sCreatedAt(pv.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setName(pv.getMetadata().getName());
        entity.setApiVersion(pv.getApiVersion());
        entity.setResourceVersion(pv.getMetadata().getResourceVersion());
        entity.setPhase(pv.getStatus().getPhase());
        entity.setAccessModes(serialize(pv.getSpec().getAccessModes()));
        entity.setCapacity(
                pv.getSpec().getCapacity() != null ? pv.getSpec().getCapacity().get("storage").toString() : null);
        entity.setStorageClassName(pv.getSpec().getStorageClassName());
        entity.setPersistentVolumeReclaimPolicy(pv.getSpec().getPersistentVolumeReclaimPolicy());
        entity.setOwnerRefs(serialize(pv.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(pv.getMetadata().getLabels()));
        entity.setAnnotations(serialize(pv.getMetadata().getAnnotations()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());
        pvRepository.save(entity);
    }

    @Transactional
    public void markPVDeleted(String uid) {
        pvRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            pvRepository.save(entity);
            log.info("Successfully marked PV {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark PV {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncNamespace(Long clusterId, io.fabric8.kubernetes.api.model.Namespace namespace) {
        String uid = namespace.getMetadata().getUid();
        K8sNamespace entity = namespaceRepository.findByUid(uid)
                .orElseGet(() -> {
                    K8sNamespace newNs = new K8sNamespace();
                    newNs.setUid(uid);
                    return newNs;
                });
        entity.setK8sCreatedAt(namespace.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setName(namespace.getMetadata().getName());
        entity.setApiVersion(namespace.getApiVersion());
        entity.setResourceVersion(namespace.getMetadata().getResourceVersion());
        entity.setStatus(namespace.getStatus().getPhase());
        entity.setOwnerRefs(serialize(namespace.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(namespace.getMetadata().getLabels()));
        entity.setAnnotations(serialize(namespace.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(namespace.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        namespaceRepository.save(entity);
    }

    @Transactional
    public void markNamespaceDeleted(String uid) {
        namespaceRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            namespaceRepository.save(entity);
            log.info("Successfully marked Namespace {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark Namespace {} as deleted: not found in DB", uid));
    }

    @Transactional
    public void syncPVC(Long clusterId, io.fabric8.kubernetes.api.model.PersistentVolumeClaim pvc) {
        if (!ensureNamespace(pvc.getMetadata(), "PVC", pvc.getMetadata().getName()))
            return;
        String uid = pvc.getMetadata().getUid();
        PersistentVolumeClaim entity = pvcRepository.findByUid(uid)
                .orElseGet(() -> {
                    PersistentVolumeClaim newPvc = new PersistentVolumeClaim();
                    newPvc.setUid(uid);
                    return newPvc;
                });
        entity.setClusterId(clusterId);
        entity.setNamespace(pvc.getMetadata().getNamespace());
        entity.setName(pvc.getMetadata().getName());
        entity.setApiVersion(pvc.getApiVersion());
        entity.setResourceVersion(pvc.getMetadata().getResourceVersion());
        entity.setPhase(pvc.getStatus().getPhase());
        entity.setAccessModes(serialize(pvc.getSpec().getAccessModes()));
        entity.setCapacity(
                pvc.getStatus().getCapacity() != null ? pvc.getStatus().getCapacity().get("storage").toString() : null);
        entity.setVolumeName(pvc.getSpec().getVolumeName());
        entity.setStorageClassName(pvc.getSpec().getStorageClassName());
        entity.setOwnerRefs(serialize(pvc.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(pvc.getMetadata().getLabels()));
        entity.setAnnotations(serialize(pvc.getMetadata().getAnnotations()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());
        pvcRepository.save(entity);
    }

    @Transactional
    public void markPVCDeleted(String uid) {
        pvcRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            pvcRepository.save(entity);
            log.info("Successfully marked PVC {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark PVC {} as deleted: not found in DB", uid));
    }

    public void reconcileCluster(Long clusterId, KubernetesClient client) {
        log.info("Starting reconciliation for cluster {}", clusterId);

        // PODS
        try {
            Set<String> k8sUids = client.pods().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = podRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(Pod::getUid).collect(Collectors.toList());
            log.info("Reconciling Pods for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markPodDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Pod {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Pods for cluster {}", clusterId, e);
        }

        // SERVICES
        try {
            Set<String> k8sUids = client.services().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = serviceRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(com.k8s.platform.domain.entity.k8s.Service::getUid).collect(Collectors.toList());
            log.info("Reconciling Services for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markServiceDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Service {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Services for cluster {}", clusterId, e);
        }

        // DEPLOYMENTS
        try {
            Set<String> k8sUids = client.apps().deployments().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = deploymentRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(Deployment::getUid).collect(Collectors.toList());
            log.info("Reconciling Deployments for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markDeploymentDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Deployment {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Deployments for cluster {}", clusterId, e);
        }

        // INGRESSES
        try {
            Set<String> k8sUids = client.network().v1().ingresses().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = ingressRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(Ingress::getUid).collect(Collectors.toList());
            log.info("Reconciling Ingresses for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markIngressDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Ingress {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Ingresses for cluster {}", clusterId, e);
        }

        // NODES
        try {
            Set<String> k8sUids = client.nodes().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = nodeRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(K8sNode::getUid).collect(Collectors.toList());
            log.info("Reconciling Nodes for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markNodeDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Node {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Nodes for cluster {}", clusterId, e);
        }

        // NAMESPACES
        try {
            Set<String> k8sUids = client.namespaces().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = namespaceRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(K8sNamespace::getUid).collect(Collectors.toList());
            log.info("Reconciling Namespaces for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markNamespaceDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Namespace {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Namespaces for cluster {}", clusterId, e);
        }

        // DAEMONSETS
        try {
            Set<String> k8sUids = client.apps().daemonSets().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = daemonSetRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(DaemonSet::getUid).collect(Collectors.toList());
            log.info("Reconciling DaemonSets for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markDaemonSetDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark DaemonSet {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile DaemonSets for cluster {}", clusterId, e);
        }

        // STATEFULSETS
        try {
            Set<String> k8sUids = client.apps().statefulSets().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = statefulSetRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(StatefulSet::getUid).collect(Collectors.toList());
            log.info("Reconciling StatefulSets for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markStatefulSetDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark StatefulSet {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile StatefulSets for cluster {}", clusterId, e);
        }

        // CONFIGMAPS
        try {
            Set<String> k8sUids = client.configMaps().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = configMapRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(com.k8s.platform.domain.entity.k8s.ConfigMap::getUid).collect(Collectors.toList());
            log.info("Reconciling ConfigMaps for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markConfigMapDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark ConfigMap {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile ConfigMaps for cluster {}", clusterId, e);
        }

        // SECRETS
        try {
            Set<String> k8sUids = client.secrets().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = secretRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(com.k8s.platform.domain.entity.k8s.Secret::getUid).collect(Collectors.toList());
            log.info("Reconciling Secrets for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markSecretDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Secret {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Secrets for cluster {}", clusterId, e);
        }

        // JOBS
        try {
            Set<String> k8sUids = client.batch().v1().jobs().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = jobRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(Job::getUid).collect(Collectors.toList());
            log.info("Reconciling Jobs for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markJobDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Job {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Jobs for cluster {}", clusterId, e);
        }

        // CRONJOBS
        try {
            Set<String> k8sUids = client.batch().v1().cronjobs().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = cronJobRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(CronJob::getUid).collect(Collectors.toList());
            log.info("Reconciling CronJobs for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markCronJobDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark CronJob {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile CronJobs for cluster {}", clusterId, e);
        }

        // ENDPOINTSLICES
        try {
            Set<String> k8sUids = client.discovery().v1().endpointSlices().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = endpointSliceRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(EndpointSlice::getUid).collect(Collectors.toList());
            log.info("Reconciling EndpointSlices for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markEndpointSliceDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark EndpointSlice {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile EndpointSlices for cluster {}", clusterId, e);
        }

        // PVCS
        try {
            Set<String> k8sUids = client.persistentVolumeClaims().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = pvcRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(PersistentVolumeClaim::getUid).collect(Collectors.toList());
            log.info("Reconciling PVCs for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markPVCDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark PVC {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile PVCs for cluster {}", clusterId, e);
        }

        // PVS
        try {
            Set<String> k8sUids = client.persistentVolumes().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = pvRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(PersistentVolume::getUid).collect(Collectors.toList());
            log.info("Reconciling PVs for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markPVDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark PV {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile PVs for cluster {}", clusterId, e);
        }

        // REPLICASETS
        try {
            Set<String> k8sUids = client.apps().replicaSets().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = replicaSetRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(ReplicaSet::getUid).collect(Collectors.toList());
            log.info("Reconciling ReplicaSets for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markReplicaSetDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark ReplicaSet {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile ReplicaSets for cluster {}", clusterId, e);
        }

        // REPLICATIONCONTROLLERS
        try {
            Set<String> k8sUids = client.replicationControllers().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = replicationControllerRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(ReplicationController::getUid).collect(Collectors.toList());
            log.info("Reconciling ReplicationControllers for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markReplicationControllerDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Repl.Controller {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile ReplicationControllers for cluster {}", clusterId, e);
        }

        // CLUSTERROLES
        try {
            Set<String> k8sUids = client.rbac().clusterRoles().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = clusterRoleRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(ClusterRole::getUid).collect(Collectors.toList());
            log.info("Reconciling ClusterRoles for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markClusterRoleDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark ClusterRole {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile ClusterRoles for cluster {}", clusterId, e);
        }

        // CLUSTERROLEBINDINGS
        try {
            Set<String> k8sUids = client.rbac().clusterRoleBindings().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = clusterRoleBindingRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(ClusterRoleBinding::getUid).collect(Collectors.toList());
            log.info("Reconciling ClusterRoleBindings for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markClusterRoleBindingDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark ClusterRoleBinding {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile ClusterRoleBindings for cluster {}", clusterId, e);
        }

        // ROLES
        try {
            Set<String> k8sUids = client.rbac().roles().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = k8sRoleRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(K8sRole::getUid).collect(Collectors.toList());
            log.info("Reconciling Roles for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markRoleDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Role {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Roles for cluster {}", clusterId, e);
        }

        // ROLEBINDINGS
        try {
            Set<String> k8sUids = client.rbac().roleBindings().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = roleBindingRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(RoleBinding::getUid).collect(Collectors.toList());
            log.info("Reconciling RoleBindings for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markRoleBindingDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark RoleBinding {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile RoleBindings for cluster {}", clusterId, e);
        }

        // SERVICEACCOUNTS
        try {
            Set<String> k8sUids = client.serviceAccounts().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = serviceAccountRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(ServiceAccount::getUid).collect(Collectors.toList());
            log.info("Reconciling ServiceAccounts for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markServiceAccountDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark ServiceAccount {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile ServiceAccounts for cluster {}", clusterId, e);
        }

        // MUTATINGWEBHOOKCONFIGURATIONS
        try {
            Set<String> k8sUids = client.admissionRegistration().v1().mutatingWebhookConfigurations().list().getItems()
                    .stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = mutatingWebhookConfigurationRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                    .stream()
                    .map(MutatingWebhookConfiguration::getUid).collect(Collectors.toList());
            log.info("Reconciling MutatingWebhookConfigurations for cluster {}: found {} in K8s, {} active in DB",
                    clusterId, k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markMutatingWebhookConfigurationDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark MutatingWebhookConfiguration {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile MutatingWebhookConfigurations for cluster {}", clusterId, e);
        }

        // VALIDATINGWEBHOOKCONFIGURATIONS
        try {
            Set<String> k8sUids = client.admissionRegistration().v1().validatingWebhookConfigurations().list()
                    .getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = validatingWebhookConfigurationRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                    .stream()
                    .map(ValidatingWebhookConfiguration::getUid).collect(Collectors.toList());
            log.info("Reconciling ValidatingWebhookConfigurations for cluster {}: found {} in K8s, {} active in DB",
                    clusterId, k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markValidatingWebhookConfigurationDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark ValidatingWebhookConfiguration {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile ValidatingWebhookConfigurations for cluster {}", clusterId, e);
        }

        // CERTIFICATESIGNINGREQUESTS
        try {
            Set<String> k8sUids = client.certificates().v1().certificateSigningRequests().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = certificateSigningRequestRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                    .stream()
                    .map(CertificateSigningRequest::getUid).collect(Collectors.toList());
            log.info("Reconciling CSRs for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markCertificateSigningRequestDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark CSR {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile CSRs for cluster {}", clusterId, e);
        }

        // CSIDRIVERS
        try {
            Set<String> k8sUids = client.storage().v1().csiDrivers().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = csiDriverRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(CSIDriver::getUid).collect(Collectors.toList());
            log.info("Reconciling CSIDrivers for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markCSIDriverDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark CSIDriver {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile CSIDrivers for cluster {}", clusterId, e);
        }

        // CSINODES
        try {
            Set<String> k8sUids = client.storage().v1().csiNodes().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = csiNodeRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(CSINode::getUid).collect(Collectors.toList());
            log.info("Reconciling CSINodes for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markCSINodeDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark CSINode {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile CSINodes for cluster {}", clusterId, e);
        }

        // CUSTOMRESOURCEDEFINITIONS
        try {
            Set<String> k8sUids = client.apiextensions().v1().customResourceDefinitions().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = customResourceDefinitionRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                    .stream()
                    .map(CustomResourceDefinition::getUid).collect(Collectors.toList());
            log.info("Reconciling CRDs for cluster {}: found {} in K8s, {} active in DB", clusterId, k8sUids.size(),
                    dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markCustomResourceDefinitionDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark CRD {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile CRDs for cluster {}", clusterId, e);
        }

        // INGRESSCLASSES
        try {
            Set<String> k8sUids = client.network().v1().ingressClasses().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = ingressClassRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(IngressClass::getUid).collect(Collectors.toList());
            log.info("Reconciling IngressClasses for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markIngressClassDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark IngressClass {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile IngressClasses for cluster {}", clusterId, e);
        }

        // IPADDRESSES (v1alpha1 - optional API, may not be available on all clusters)
        try {
            Set<String> k8sUids = client.network().v1alpha1().ipAddresses().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = ipAddressRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(IPAddress::getUid).collect(Collectors.toList());
            log.info("Reconciling IPAddresses for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markIPAddressDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark IPAddress {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("IPAddress API (v1alpha1) not available for cluster {}: {}", clusterId, e.getMessage());
        }

        // PRIORITYCLASSES
        try {
            Set<String> k8sUids = client.scheduling().v1().priorityClasses().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = priorityClassRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(PriorityClass::getUid).collect(Collectors.toList());
            log.info("Reconciling PriorityClasses for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markPriorityClassDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark PriorityClass {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile PriorityClasses for cluster {}", clusterId, e);
        }

        // PRIORITYLEVELCONFIGURATIONS (v1beta3 - optional API, may not be available on
        // all clusters)
        try {
            Set<String> k8sUids = client.flowControl().v1beta3().priorityLevelConfigurations().list().getItems()
                    .stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = priorityLevelConfigurationRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                    .stream()
                    .map(PriorityLevelConfiguration::getUid).collect(Collectors.toList());
            log.info("Reconciling PriorityLevelConfigurations for cluster {}: found {} in K8s, {} active in DB",
                    clusterId, k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markPriorityLevelConfigurationDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark PriorityLevelConfiguration {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("PriorityLevelConfiguration API (v1beta3) not available for cluster {}: {}", clusterId,
                    e.getMessage());
        }

        // VALIDATINGADMISSIONPOLICIES (v1beta1 - optional API, may not be available on
        // all clusters)
        try {
            Set<String> k8sUids = client.admissionRegistration().v1beta1().validatingAdmissionPolicies().list()
                    .getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = validatingAdmissionPolicyRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                    .stream()
                    .map(ValidatingAdmissionPolicy::getUid).collect(Collectors.toList());
            log.info("Reconciling ValidatingAdmissionPolicies for cluster {}: found {} in K8s, {} active in DB",
                    clusterId, k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markValidatingAdmissionPolicyDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark ValidatingAdmissionPolicy {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("ValidatingAdmissionPolicy API (v1beta1) not available for cluster {}: {}", clusterId,
                    e.getMessage());
        }

        // VALIDATINGADMISSIONPOLICYBINDINGS (v1beta1 - optional API, may not be
        // available on all clusters)
        try {
            Set<String> k8sUids = client.admissionRegistration().v1beta1().validatingAdmissionPolicyBindings().list()
                    .getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = validatingAdmissionPolicyBindingRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                    .stream()
                    .map(ValidatingAdmissionPolicyBinding::getUid).collect(Collectors.toList());
            log.info("Reconciling ValidatingAdmissionPolicyBindings for cluster {}: found {} in K8s, {} active in DB",
                    clusterId, k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markValidatingAdmissionPolicyBindingDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark ValidatingAdmissionPolicyBinding {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("ValidatingAdmissionPolicyBinding API (v1beta1) not available for cluster {}: {}", clusterId,
                    e.getMessage());
        }

        // VOLUMEATTACHMENTS
        try {
            Set<String> k8sUids = client.storage().v1().volumeAttachments().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = volumeAttachmentRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(VolumeAttachment::getUid).collect(Collectors.toList());
            log.info("Reconciling VolumeAttachments for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markVolumeAttachmentDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark VolumeAttachment {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile VolumeAttachments for cluster {}", clusterId, e);
        }

        // LEASES
        try {
            Set<String> k8sUids = client.leases().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = leaseRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(Lease::getUid).collect(Collectors.toList());
            log.info("Reconciling Leases for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markLeaseDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Lease {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Leases for cluster {}", clusterId, e);
        }

        // EVENTS
        try {
            Set<String> k8sUids = client.v1().events().inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = eventRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(K8sEvent::getUid).collect(Collectors.toList());
            log.info("Reconciling Events for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markEventDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark Event {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile Events for cluster {}", clusterId, e);
        }

        // HPAs
        try {
            Set<String> k8sUids = client.autoscaling().v2().horizontalPodAutoscalers()
                    .inAnyNamespace().list().getItems().stream()
                    .map(r -> r.getMetadata().getUid()).collect(Collectors.toSet());
            List<String> dbUids = hpaRepository.findByClusterIdAndIsDeletedFalse(clusterId).stream()
                    .map(com.k8s.platform.domain.entity.k8s.HorizontalPodAutoscaler::getUid)
                    .collect(Collectors.toList());
            log.info("Reconciling HPAs for cluster {}: found {} in K8s, {} active in DB", clusterId,
                    k8sUids.size(), dbUids.size());
            dbUids.stream().filter(uid -> !k8sUids.contains(uid)).forEach(uid -> {
                try {
                    markHpaDeleted(uid);
                } catch (Exception e) {
                    log.error("Failed to mark HPA {} as deleted: {}", uid, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Failed to reconcile HPAs for cluster {}", clusterId, e);
        }

        log.info("Reconciliation completed for cluster {}", clusterId);
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse Kubernetes timestamp: {}", timestamp);
            return null;
        }
    }

    private String extractNodeRoles(java.util.Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "worker";
        }

        java.util.List<String> roles = new java.util.ArrayList<>();

        // Check for control-plane/master labels
        if (labels.containsKey("node-role.kubernetes.io/control-plane") ||
                labels.containsKey("node-role.kubernetes.io/master")) {
            roles.add("master");
        }

        // Check for worker label
        if (labels.containsKey("node-role.kubernetes.io/worker")) {
            roles.add("worker");
        }

        // If no specific role found, default to worker
        if (roles.isEmpty()) {
            return "worker";
        }

        return String.join(",", roles);
    }

    private String serialize(Object obj) {
        if (obj == null)
            return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON", e);
            return null;
        }
    }

    @Transactional
    public void syncPersistentVolume(Long clusterId, io.fabric8.kubernetes.api.model.PersistentVolume pv) {
        String uid = pv.getMetadata().getUid();
        PersistentVolume entity = pvRepository.findByUid(uid)
                .orElseGet(() -> {
                    PersistentVolume newPv = new PersistentVolume();
                    newPv.setUid(uid);
                    return newPv;
                });
        entity.setK8sCreatedAt(pv.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setName(pv.getMetadata().getName());
        entity.setApiVersion(pv.getApiVersion());
        entity.setResourceVersion(pv.getMetadata().getResourceVersion());
        entity.setGeneration(
                pv.getMetadata().getGeneration() != null ? pv.getMetadata().getGeneration().intValue() : null);

        // Spec fields
        if (pv.getSpec() != null) {
            entity.setAccessModes(serialize(pv.getSpec().getAccessModes()));
            entity.setCapacity(serialize(pv.getSpec().getCapacity()));
            entity.setStorageClassName(pv.getSpec().getStorageClassName());
            entity.setVolumeMode(pv.getSpec().getVolumeMode());
            entity.setPersistentVolumeReclaimPolicy(pv.getSpec().getPersistentVolumeReclaimPolicy());
        }

        // Status fields
        if (pv.getStatus() != null) {
            entity.setPhase(pv.getStatus().getPhase());
            entity.setMessage(pv.getStatus().getMessage());
            entity.setReason(pv.getStatus().getReason());
        }

        entity.setOwnerRefs(serialize(pv.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(pv.getMetadata().getLabels()));
        entity.setAnnotations(serialize(pv.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(pv.getMetadata().getManagedFields()));
        entity.setK8sCreatedAt(pv.getMetadata().getCreationTimestamp());
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        pvRepository.save(entity);
    }

    @Transactional
    public void syncReplicaSet(Long clusterId, io.fabric8.kubernetes.api.model.apps.ReplicaSet rs) {
        if (!ensureNamespace(rs.getMetadata(), "ReplicaSet",
                rs.getMetadata() != null ? rs.getMetadata().getName() : "<unknown>"))
            return;
        String uid = rs.getMetadata().getUid();
        ReplicaSet entity = replicaSetRepository.findByUid(uid)
                .orElseGet(() -> {
                    ReplicaSet newRs = new ReplicaSet();
                    newRs.setUid(uid);
                    return newRs;
                });
        entity.setK8sCreatedAt(rs.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(rs.getMetadata().getNamespace());
        entity.setName(rs.getMetadata().getName());
        entity.setApiVersion(rs.getApiVersion());
        entity.setResourceVersion(rs.getMetadata().getResourceVersion());
        entity.setGeneration(
                rs.getMetadata().getGeneration() != null ? rs.getMetadata().getGeneration().intValue() : null);

        // Spec fields
        if (rs.getSpec() != null) {
            entity.setReplicas(rs.getSpec().getReplicas());
            entity.setMinReadySeconds(rs.getSpec().getMinReadySeconds());
        }

        // Status fields
        if (rs.getStatus() != null) {
            entity.setReplicas(rs.getStatus().getReplicas());
            entity.setReadyReplicas(rs.getStatus().getReadyReplicas());
            entity.setAvailableReplicas(rs.getStatus().getAvailableReplicas());
            entity.setFullyLabeledReplicas(rs.getStatus().getFullyLabeledReplicas());
            entity.setObservedGeneration(
                    rs.getStatus().getObservedGeneration() != null ? rs.getStatus().getObservedGeneration().intValue()
                            : null);
            entity.setConditions(serialize(rs.getStatus().getConditions()));
        }

        entity.setOwnerRefs(serialize(rs.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(rs.getMetadata().getLabels()));
        entity.setAnnotations(serialize(rs.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(rs.getMetadata().getManagedFields()));
        entity.setK8sCreatedAt(rs.getMetadata().getCreationTimestamp());
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        replicaSetRepository.save(entity);
    }

    @Transactional
    public void markReplicaSetDeleted(String uid) {
        replicaSetRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            replicaSetRepository.save(entity);
            log.info("Successfully marked ReplicaSet {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark ReplicaSet {} as deleted: not found in DB", uid));
    }

    // RBAC Sync Methods

    @Transactional
    public void syncClusterRole(Long clusterId, io.fabric8.kubernetes.api.model.rbac.ClusterRole resource) {
        String uid = resource.getMetadata().getUid();
        ClusterRole entity = clusterRoleRepository.findByUid(uid)
                .orElseGet(() -> {
                    ClusterRole newEntity = new ClusterRole();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        entity.setAggregationRule(serialize(resource.getAggregationRule()));
        entity.setRules(serialize(resource.getRules()));

        clusterRoleRepository.save(entity);
    }

    @Transactional
    public void markClusterRoleDeleted(String uid) {
        clusterRoleRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            clusterRoleRepository.save(entity);
        });
    }

    @Transactional
    public void syncClusterRoleBinding(Long clusterId,
            io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding resource) {
        String uid = resource.getMetadata().getUid();
        ClusterRoleBinding entity = clusterRoleBindingRepository.findByUid(uid)
                .orElseGet(() -> {
                    ClusterRoleBinding newEntity = new ClusterRoleBinding();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        entity.setRoleRef(serialize(resource.getRoleRef()));
        entity.setSubjects(serialize(resource.getSubjects()));

        clusterRoleBindingRepository.save(entity);
    }

    @Transactional
    public void markClusterRoleBindingDeleted(String uid) {
        clusterRoleBindingRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            clusterRoleBindingRepository.save(entity);
        });
    }

    @Transactional
    public void syncRole(Long clusterId, io.fabric8.kubernetes.api.model.rbac.Role resource) {
        if (!ensureNamespace(resource.getMetadata(), "Role", resource.getMetadata().getName()))
            return;

        String uid = resource.getMetadata().getUid();
        K8sRole entity = k8sRoleRepository.findByUid(uid)
                .orElseGet(() -> {
                    K8sRole newEntity = new K8sRole();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        entity.setRules(serialize(resource.getRules()));

        k8sRoleRepository.save(entity);
    }

    @Transactional
    public void markRoleDeleted(String uid) {
        k8sRoleRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            k8sRoleRepository.save(entity);
        });
    }

    @Transactional
    public void syncRoleBinding(Long clusterId, io.fabric8.kubernetes.api.model.rbac.RoleBinding resource) {
        if (!ensureNamespace(resource.getMetadata(), "RoleBinding", resource.getMetadata().getName()))
            return;

        String uid = resource.getMetadata().getUid();
        RoleBinding entity = roleBindingRepository.findByUid(uid)
                .orElseGet(() -> {
                    RoleBinding newEntity = new RoleBinding();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        entity.setRoleRef(serialize(resource.getRoleRef()));
        entity.setSubjects(serialize(resource.getSubjects()));

        roleBindingRepository.save(entity);
    }

    @Transactional
    public void markRoleBindingDeleted(String uid) {
        roleBindingRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            roleBindingRepository.save(entity);
        });
    }

    @Transactional
    public void syncServiceAccount(Long clusterId, io.fabric8.kubernetes.api.model.ServiceAccount resource) {
        if (!ensureNamespace(resource.getMetadata(), "ServiceAccount", resource.getMetadata().getName()))
            return;

        String uid = resource.getMetadata().getUid();
        ServiceAccount entity = serviceAccountRepository.findByUid(uid)
                .orElseGet(() -> {
                    ServiceAccount newEntity = new ServiceAccount();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        entity.setAutomountServiceAccountToken(resource.getAutomountServiceAccountToken());
        entity.setImagePullSecrets(serialize(resource.getImagePullSecrets()));
        entity.setSecrets(serialize(resource.getSecrets()));

        serviceAccountRepository.save(entity);
    }

    @Transactional
    public void markServiceAccountDeleted(String uid) {
        serviceAccountRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            serviceAccountRepository.save(entity);
        });
    }

    // Helper method to reduce duplication
    private void updateCommonFields(BaseK8sEntity entity, ObjectMeta meta, Long clusterId, String apiVersion) {
        if (entity == null || meta == null)
            return;

        entity.setClusterId(clusterId);
        entity.setNamespace(meta.getNamespace());
        entity.setName(meta.getName());
        entity.setApiVersion(apiVersion);
        entity.setResourceVersion(meta.getResourceVersion());
        entity.setGeneration(meta.getGeneration() != null ? meta.getGeneration().intValue() : null);
        entity.setOwnerRefs(serialize(meta.getOwnerReferences()));
        entity.setLabels(serialize(meta.getLabels()));
        entity.setAnnotations(serialize(meta.getAnnotations()));
        entity.setManagedFields(serialize(meta.getManagedFields()));
        entity.setK8sCreatedAt(meta.getCreationTimestamp());
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    // Administrator Resources Sync Methods

    @Transactional
    public void syncMutatingWebhookConfiguration(Long clusterId,
            io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfiguration resource) {
        String uid = resource.getMetadata().getUid();
        MutatingWebhookConfiguration entity = mutatingWebhookConfigurationRepository.findByUid(uid)
                .orElseGet(() -> {
                    MutatingWebhookConfiguration newEntity = new MutatingWebhookConfiguration();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        entity.setWebhooks(serialize(resource.getWebhooks()));

        mutatingWebhookConfigurationRepository.save(entity);
    }

    @Transactional
    public void markMutatingWebhookConfigurationDeleted(String uid) {
        mutatingWebhookConfigurationRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            mutatingWebhookConfigurationRepository.save(entity);
        });
    }

    @Transactional
    public void syncValidatingWebhookConfiguration(Long clusterId,
            io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration resource) {
        String uid = resource.getMetadata().getUid();
        ValidatingWebhookConfiguration entity = validatingWebhookConfigurationRepository.findByUid(uid)
                .orElseGet(() -> {
                    ValidatingWebhookConfiguration newEntity = new ValidatingWebhookConfiguration();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        entity.setWebhooks(serialize(resource.getWebhooks()));

        validatingWebhookConfigurationRepository.save(entity);
    }

    @Transactional
    public void markValidatingWebhookConfigurationDeleted(String uid) {
        validatingWebhookConfigurationRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            validatingWebhookConfigurationRepository.save(entity);
        });
    }

    // Other Resources Sync Methods (Batch 1)

    @Transactional
    public void syncCertificateSigningRequest(Long clusterId,
            io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest resource) {
        String uid = resource.getMetadata().getUid();
        CertificateSigningRequest entity = certificateSigningRequestRepository.findByUid(uid)
                .orElseGet(() -> {
                    CertificateSigningRequest newEntity = new CertificateSigningRequest();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setSignerName(resource.getSpec().getSignerName());
            entity.setUsages(serialize(resource.getSpec().getUsages()));
            entity.setRequest(resource.getSpec().getRequest()); // Store raw string if applicable or modify entity
        }
        if (resource.getStatus() != null) {
            entity.setConditions(serialize(resource.getStatus().getConditions()));
            entity.setCertificate(resource.getStatus().getCertificate());
        }

        certificateSigningRequestRepository.save(entity);
    }

    @Transactional
    public void markCertificateSigningRequestDeleted(String uid) {
        certificateSigningRequestRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            certificateSigningRequestRepository.save(entity);
        });
    }

    @Transactional
    public void syncCSIDriver(Long clusterId, io.fabric8.kubernetes.api.model.storage.CSIDriver resource) {
        String uid = resource.getMetadata().getUid();
        CSIDriver entity = csiDriverRepository.findByUid(uid)
                .orElseGet(() -> {
                    CSIDriver newEntity = new CSIDriver();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setAttachRequired(resource.getSpec().getAttachRequired());
            entity.setPodInfoOnMount(resource.getSpec().getPodInfoOnMount());
            entity.setStorageCapacity(resource.getSpec().getStorageCapacity());
            entity.setTokenRequests(serialize(resource.getSpec().getTokenRequests()));
            entity.setRequiresRepublish(resource.getSpec().getRequiresRepublish());
            entity.setVolumeLifecycleModes(serialize(resource.getSpec().getVolumeLifecycleModes()));
        }

        csiDriverRepository.save(entity);
    }

    @Transactional
    public void markCSIDriverDeleted(String uid) {
        csiDriverRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            csiDriverRepository.save(entity);
        });
    }

    @Transactional
    public void syncCSINode(Long clusterId, io.fabric8.kubernetes.api.model.storage.CSINode resource) {
        String uid = resource.getMetadata().getUid();
        CSINode entity = csiNodeRepository.findByUid(uid)
                .orElseGet(() -> {
                    CSINode newEntity = new CSINode();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setDrivers(serialize(resource.getSpec().getDrivers()));
        }

        csiNodeRepository.save(entity);
    }

    @Transactional
    public void markCSINodeDeleted(String uid) {
        csiNodeRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            csiNodeRepository.save(entity);
        });
    }

    @Transactional
    public void syncCustomResourceDefinition(Long clusterId,
            io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition resource) {
        String uid = resource.getMetadata().getUid();
        CustomResourceDefinition entity = customResourceDefinitionRepository.findByUid(uid)
                .orElseGet(() -> {
                    CustomResourceDefinition newEntity = new CustomResourceDefinition();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setGroupName(resource.getSpec().getGroup()); // Mapped to groupName in entity?
            entity.setScope(resource.getSpec().getScope());
            entity.setNames(serialize(resource.getSpec().getNames()));
            entity.setVersions(serialize(resource.getSpec().getVersions()));
        }
        if (resource.getStatus() != null) {
            entity.setConditions(serialize(resource.getStatus().getConditions()));
        }

        customResourceDefinitionRepository.save(entity);
    }

    @Transactional
    public void markCustomResourceDefinitionDeleted(String uid) {
        customResourceDefinitionRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            customResourceDefinitionRepository.save(entity);
        });
    }

    @Transactional
    public void syncIngressClass(Long clusterId, io.fabric8.kubernetes.api.model.networking.v1.IngressClass resource) {
        String uid = resource.getMetadata().getUid();
        IngressClass entity = ingressClassRepository.findByUid(uid)
                .orElseGet(() -> {
                    IngressClass newEntity = new IngressClass();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setController(resource.getSpec().getController());
            entity.setParameters(serialize(resource.getSpec().getParameters()));
        }

        ingressClassRepository.save(entity);
    }

    @Transactional
    public void markIngressClassDeleted(String uid) {
        ingressClassRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            ingressClassRepository.save(entity);
        });
    }

    @Transactional
    public void syncIPAddress(Long clusterId, io.fabric8.kubernetes.api.model.networking.v1alpha1.IPAddress resource) {
        String uid = resource.getMetadata().getUid();
        IPAddress entity = ipAddressRepository.findByUid(uid)
                .orElseGet(() -> {
                    IPAddress newEntity = new IPAddress();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setParentRef(serialize(resource.getSpec().getParentRef()));
        }

        ipAddressRepository.save(entity);
    }

    @Transactional
    public void markIPAddressDeleted(String uid) {
        ipAddressRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            ipAddressRepository.save(entity);
        });
    }

    // Other Resources Sync Methods (Batch 2)

    @Transactional
    public void syncPriorityClass(Long clusterId,
            io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClass resource) {
        String uid = resource.getMetadata().getUid();
        PriorityClass entity = priorityClassRepository.findByUid(uid)
                .orElseGet(() -> {
                    PriorityClass newEntity = new PriorityClass();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        entity.setValue(resource.getValue());
        entity.setGlobalDefault(resource.getGlobalDefault());
        entity.setPreemptionPolicy(resource.getPreemptionPolicy());
        entity.setDescription(resource.getDescription());

        priorityClassRepository.save(entity);
    }

    @Transactional
    public void markPriorityClassDeleted(String uid) {
        priorityClassRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            priorityClassRepository.save(entity);
        });
    }

    @Transactional
    public void syncPriorityLevelConfiguration(Long clusterId,
            io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration resource) {
        String uid = resource.getMetadata().getUid();
        PriorityLevelConfiguration entity = priorityLevelConfigurationRepository.findByUid(uid)
                .orElseGet(() -> {
                    PriorityLevelConfiguration newEntity = new PriorityLevelConfiguration();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setLimited(resource.getSpec().getType());
        }
        if (resource.getStatus() != null) {
            entity.setConditions(serialize(resource.getStatus().getConditions()));
        }

        priorityLevelConfigurationRepository.save(entity);
    }

    @Transactional
    public void markPriorityLevelConfigurationDeleted(String uid) {
        priorityLevelConfigurationRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            priorityLevelConfigurationRepository.save(entity);
        });
    }

    @Transactional
    public void syncValidatingAdmissionPolicy(Long clusterId,
            io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicy resource) {
        String uid = resource.getMetadata().getUid();
        ValidatingAdmissionPolicy entity = validatingAdmissionPolicyRepository.findByUid(uid)
                .orElseGet(() -> {
                    ValidatingAdmissionPolicy newEntity = new ValidatingAdmissionPolicy();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setParamKind(serialize(resource.getSpec().getParamKind()));
            entity.setMatchConstraints(serialize(resource.getSpec().getMatchConstraints()));
            entity.setValidations(serialize(resource.getSpec().getValidations()));
            entity.setFailurePolicy(resource.getSpec().getFailurePolicy());
            entity.setAuditAnnotations(serialize(resource.getSpec().getAuditAnnotations()));
            entity.setMatchConditions(serialize(resource.getSpec().getMatchConditions()));
            entity.setVariables(serialize(resource.getSpec().getVariables()));
        }
        if (resource.getStatus() != null) {
            entity.setConditions(serialize(resource.getStatus().getConditions()));
            entity.setTypeChecking(serialize(resource.getStatus().getTypeChecking()));
        }

        validatingAdmissionPolicyRepository.save(entity);
    }

    @Transactional
    public void markValidatingAdmissionPolicyDeleted(String uid) {
        validatingAdmissionPolicyRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            validatingAdmissionPolicyRepository.save(entity);
        });
    }

    @Transactional
    public void syncValidatingAdmissionPolicyBinding(Long clusterId,
            io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingAdmissionPolicyBinding resource) {
        String uid = resource.getMetadata().getUid();
        ValidatingAdmissionPolicyBinding entity = validatingAdmissionPolicyBindingRepository.findByUid(uid)
                .orElseGet(() -> {
                    ValidatingAdmissionPolicyBinding newEntity = new ValidatingAdmissionPolicyBinding();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setPolicyName(resource.getSpec().getPolicyName());
            entity.setParamRef(serialize(resource.getSpec().getParamRef()));
            entity.setMatchResources(serialize(resource.getSpec().getMatchResources()));
            entity.setValidationActions(serialize(resource.getSpec().getValidationActions()));
        }

        validatingAdmissionPolicyBindingRepository.save(entity);
    }

    @Transactional
    public void markValidatingAdmissionPolicyBindingDeleted(String uid) {
        validatingAdmissionPolicyBindingRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            validatingAdmissionPolicyBindingRepository.save(entity);
        });
    }

    @Transactional
    public void syncVolumeAttachment(Long clusterId,
            io.fabric8.kubernetes.api.model.storage.VolumeAttachment resource) {
        String uid = resource.getMetadata().getUid();
        VolumeAttachment entity = volumeAttachmentRepository.findByUid(uid)
                .orElseGet(() -> {
                    VolumeAttachment newEntity = new VolumeAttachment();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setAttacher(resource.getSpec().getAttacher());
            entity.setSource(serialize(resource.getSpec().getSource()));
            entity.setNodeName(resource.getSpec().getNodeName());
        }
        if (resource.getStatus() != null) {
            entity.setAttached(resource.getStatus().getAttached());
            entity.setAttachmentMetadata(serialize(resource.getStatus().getAttachmentMetadata()));
            entity.setDetachError(serialize(resource.getStatus().getDetachError()));
            entity.setAttachError(serialize(resource.getStatus().getAttachError()));
        }

        volumeAttachmentRepository.save(entity);
    }

    @Transactional
    public void markVolumeAttachmentDeleted(String uid) {
        volumeAttachmentRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            volumeAttachmentRepository.save(entity);
        });
    }

    // Workloads Sync Methods (Remaining)

    @Transactional
    public void syncReplicationController(Long clusterId,
            io.fabric8.kubernetes.api.model.ReplicationController resource) {
        if (!ensureNamespace(resource.getMetadata(), "ReplicationController", resource.getMetadata().getName()))
            return;

        String uid = resource.getMetadata().getUid();
        ReplicationController entity = replicationControllerRepository.findByUid(uid)
                .orElseGet(() -> {
                    ReplicationController newEntity = new ReplicationController();
                    newEntity.setUid(uid);
                    return newEntity;
                });

        updateCommonFields(entity, resource.getMetadata(), clusterId, resource.getApiVersion());
        if (resource.getSpec() != null) {
            entity.setDesiredReplicas(resource.getSpec().getReplicas());
            entity.setSelector(serialize(resource.getSpec().getSelector()));
            entity.setTemplate(serialize(resource.getSpec().getTemplate()));
        }
        if (resource.getStatus() != null) {
            entity.setCurrentReplicas(resource.getStatus().getReplicas());
            entity.setReadyReplicas(resource.getStatus().getReadyReplicas());
            entity.setAvailableReplicas(resource.getStatus().getAvailableReplicas());
            entity.setFullyLabeledReplicas(resource.getStatus().getFullyLabeledReplicas());
            entity.setObservedGeneration(resource.getStatus().getObservedGeneration() != null
                    ? resource.getStatus().getObservedGeneration().intValue()
                    : null);
            entity.setConditions(serialize(resource.getStatus().getConditions()));
        }

        replicationControllerRepository.save(entity);
    }

    @Transactional
    public void markReplicationControllerDeleted(String uid) {
        replicationControllerRepository.findByUid(uid).ifPresent(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            replicationControllerRepository.save(entity);
        });
    }

    // ── HPA ──────────────────────────────────────────────────────────────────

    @Transactional
    public void syncHpa(Long clusterId,
            io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler hpa) {
        if (!ensureNamespace(hpa.getMetadata(), "HPA",
                hpa.getMetadata() != null ? hpa.getMetadata().getName() : "<unknown>"))
            return;
        String uid = hpa.getMetadata().getUid();
        com.k8s.platform.domain.entity.k8s.HorizontalPodAutoscaler entity = hpaRepository.findByUid(uid)
                .orElseGet(() -> {
                    com.k8s.platform.domain.entity.k8s.HorizontalPodAutoscaler newHpa =
                            new com.k8s.platform.domain.entity.k8s.HorizontalPodAutoscaler();
                    newHpa.setUid(uid);
                    return newHpa;
                });
        entity.setK8sCreatedAt(hpa.getMetadata().getCreationTimestamp());
        entity.setClusterId(clusterId);
        entity.setNamespace(hpa.getMetadata().getNamespace());
        entity.setName(hpa.getMetadata().getName());
        entity.setApiVersion(hpa.getApiVersion());
        entity.setResourceVersion(hpa.getMetadata().getResourceVersion());
        entity.setGeneration(
                hpa.getMetadata().getGeneration() != null ? hpa.getMetadata().getGeneration().intValue() : null);

        // HPA-specific fields
        entity.setScaleTargetRef(serialize(hpa.getSpec().getScaleTargetRef()));
        entity.setMinReplicas(hpa.getSpec().getMinReplicas());
        entity.setMaxReplicas(hpa.getSpec().getMaxReplicas());
        entity.setMetrics(serialize(hpa.getSpec().getMetrics()));
        entity.setBehavior(serialize(hpa.getSpec().getBehavior()));

        if (hpa.getStatus() != null) {
            entity.setCurrentReplicas(hpa.getStatus().getCurrentReplicas());
            entity.setDesiredReplicas(hpa.getStatus().getDesiredReplicas());
            entity.setCurrentMetrics(serialize(hpa.getStatus().getCurrentMetrics()));
            entity.setConditions(serialize(hpa.getStatus().getConditions()));
        }

        entity.setOwnerRefs(serialize(hpa.getMetadata().getOwnerReferences()));
        entity.setLabels(serialize(hpa.getMetadata().getLabels()));
        entity.setAnnotations(serialize(hpa.getMetadata().getAnnotations()));
        entity.setManagedFields(serialize(hpa.getMetadata().getManagedFields()));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());

        hpaRepository.save(entity);
    }

    @Transactional
    public void markHpaDeleted(String uid) {
        hpaRepository.findByUid(uid).ifPresentOrElse(entity -> {
            entity.setIsDeleted(true);
            entity.setDeletedAt(LocalDateTime.now());
            hpaRepository.save(entity);
            log.info("Successfully marked HPA {} as deleted in DB", uid);
        }, () -> log.warn("Failed to mark HPA {} as deleted: not found in DB", uid));
    }

}
