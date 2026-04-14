package com.k8s.platform.service.federation;

import com.k8s.platform.domain.entity.federation.Federation;
import com.k8s.platform.domain.entity.federation.FederationMember;
import com.k8s.platform.domain.entity.federation.FederationResource;
import com.k8s.platform.repository.federation.FederationMemberRepository;
import com.k8s.platform.repository.federation.FederationRepository;
import com.k8s.platform.repository.federation.FederationResourceRepository;
import com.k8s.platform.service.k8s.K8sClientService;
import com.k8s.platform.service.audit.AuditLogService;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.CommandLineRunner;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WatcherFederationService implements CommandLineRunner {

    private final FederationRepository federationRepository;
    private final FederationMemberRepository federationMemberRepository;
    private final FederationResourceRepository federationResourceRepository;
    private final K8sClientService k8sClientService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode
    @Builder
    public static class Dependency {
        private String kind;
        private String name;
        private String status; // Synced, Conflict, Missing

        public Dependency(String kind, String name) {
            this.kind = kind;
            this.name = name;
        }
    }

    private final Map<Long, Watch> activeWatchers = new ConcurrentHashMap<>();
    private final Map<String, List<Watch>> namespaceDependencyWatchers = new ConcurrentHashMap<>();
    
    // Per-resource state tracking
    private final Map<Long, ReentrantLock> resourceLocks = new ConcurrentHashMap<>();
    
    // Global lock for the scheduler
    private final ReentrantLock schedulerLock = new ReentrantLock();

    @Autowired
    @Lazy
    private WatcherFederationService self;

    @Override
    public void run(String... args) {
        log.info("[Federation Watcher] Application ready (CommandLineRunner), initializing all watchers...");
        refreshAllWatchers();
    }

    @PostConstruct
    public void init() {
        log.info("[Federation Watcher] Service bean created.");
    }

    @PreDestroy
    public void cleanup() {
        log.info("[Federation Watcher] Cleaning up watchers...");
        activeWatchers.values().forEach(Watch::close);
        activeWatchers.clear();
        namespaceDependencyWatchers.values().forEach(list -> list.forEach(Watch::close));
        namespaceDependencyWatchers.clear();
    }

    public synchronized void refreshAllWatchers() {
        // Close existing
        activeWatchers.values().forEach(Watch::close);
        activeWatchers.clear();
        namespaceDependencyWatchers.values().forEach(list -> list.forEach(Watch::close));
        namespaceDependencyWatchers.clear();

        List<FederationResource> resources = federationResourceRepository.findAll();
        log.info("[Federation Watcher] Found {} resources to watch.", resources.size());
        for (FederationResource resource : resources) {
            startWatcher(resource);
        }
    }

    public void startWatcher(FederationResource resource) {
        if (activeWatchers.containsKey(resource.getId())) {
            activeWatchers.get(resource.getId()).close();
            activeWatchers.remove(resource.getId());
        }

        try {
            Federation federation = resource.getFederation();
            if (federation == null || federation.getMasterCluster() == null) {
                log.error("[Federation Watcher] Skipping resource {}: incomplete federation/cluster data", resource.getId());
                return;
            }

            KubernetesClient masterClient = k8sClientService.getClient(federation.getMasterCluster().getUid());
            String apiVersion = getApiVersion(resource.getKind());
            
            log.info("[Federation Watcher] Setting up watcher for {} {}/{} on cluster {}", 
                    resource.getKind(), resource.getNamespace(), resource.getName(), federation.getMasterCluster().getName());

            Watch watch = masterClient.genericKubernetesResources(apiVersion, resource.getKind())
                    .inNamespace(resource.getNamespace())
                    .watch(new Watcher<GenericKubernetesResource>() {
                        @Override
                        public void eventReceived(Action action, GenericKubernetesResource resourceObj) {
                            String resourceName = (resourceObj.getMetadata() != null) ? resourceObj.getMetadata().getName() : null;
                            if (!resource.getName().equals(resourceName)) return;

                            log.info("[Federation Watcher] Event RECEIVED: {} for {}/{}", action, resource.getNamespace(), resource.getName());
                            if (action == Action.MODIFIED || action == Action.ADDED || action == Action.DELETED) {
                                self.triggerSyncForResource(resource.getId());
                            }
                        }

                        @Override
                        public void onClose(WatcherException cause) {
                            if (cause != null) {
                                log.error("[Federation Watcher] Watcher CLOSED WITH ERROR for {}/{} (ID: {}): {}", 
                                        resource.getNamespace(), resource.getName(), resource.getId(), cause.getMessage());
                                
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        TimeUnit.SECONDS.sleep(5);
                                        FederationResource freshRes = federationResourceRepository.findById(resource.getId()).orElse(null);
                                        if (freshRes != null) {
                                            log.info("[Federation Watcher] Auto-restarting watcher for resource {} ({})", freshRes.getId(), freshRes.getName());
                                            startWatcher(freshRes);
                                        }
                                    } catch (Exception e) {
                                        log.error("[Federation Watcher] Failed to auto-restart watcher: {}", e.getMessage());
                                    }
                                });
                            }
                        }
                    });

            activeWatchers.put(resource.getId(), watch);
            log.info("[Federation Watcher] ACTIVE: Watcher started for {}/{}", resource.getNamespace(), resource.getName());
            startNamespaceDependencyWatcher(federation.getMasterCluster().getUid(), resource.getNamespace(), masterClient);
            
        } catch (Exception e) {
            log.error("[Federation Watcher] FAILED to start watcher for resource {}: {}", resource.getId(), e.getMessage());
        }
    }

    @Async
    public void triggerSyncForResource(Long resourceId) {
        log.info("[Federation Watcher] Triggering sync for resource ID: {}", resourceId);
        ReentrantLock lock = resourceLocks.computeIfAbsent(resourceId, k -> new ReentrantLock());
        boolean locked = false;
        try {
            locked = lock.tryLock(10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("[Federation Watcher] Sync skip for resource {}, lock timeout - sync is probably running.", resourceId);
                return;
            }
            FederationResource resource = federationResourceRepository.findById(resourceId).orElse(null);
            if (resource != null) self.syncSingleResource(resource);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked) lock.unlock();
        }
    }

    @Scheduled(fixedDelayString = "${federation.sync.fixed-delay:300000}")
    public void syncAllFederations() {
        log.info("[Federation Scheduler] Starting periodic sync");
        boolean locked = false;
        try {
            locked = schedulerLock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) return;
            List<Federation> federations = federationRepository.findAll();
            for (Federation fed : federations) self.triggerSync(fed.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked) schedulerLock.unlock();
        }
    }

    @Async
    @Transactional
    public void triggerSync(Long federationId) {
        Federation federation = federationRepository.findById(federationId).orElse(null);
        if (federation == null) return;
        log.info(">>>> [Federation Sync] STARTING: {} (ID: {})", federation.getName(), federationId);
        try {
            List<FederationResource> resources = federationResourceRepository.findByFederationId(federationId);
            if (resources.isEmpty()) {
                federation.setStatus("Success");
                federationRepository.save(federation);
                return;
            }
            boolean allSuccess = true;
            for (FederationResource resource : resources) {
                if (!syncSingleResource(resource)) allSuccess = false;
            }
            federation.setStatus(allSuccess ? "Success" : "Error");
            federationRepository.save(federation);
            log.info(">>>> [Federation Sync] COMPLETED: {} Status: {}", federation.getName(), federation.getStatus());
        } catch (Exception e) {
            log.error("[Federation Sync] FATAL: {}", e.getMessage());
            federation.setStatus("Error");
            federationRepository.save(federation);
        }
    }

    @Transactional
    public boolean syncSingleResource(FederationResource resource) {
        Federation federation = resource.getFederation();
        log.info("[Federation Sync] --- Processing: {}/{} ({}) ---", resource.getNamespace(), resource.getName(), resource.getKind());
        try {
            KubernetesClient masterClient = k8sClientService.getClient(federation.getMasterCluster().getUid());
            List<FederationMember> members = federationMemberRepository.findByFederationId(federation.getId());
            GenericKubernetesResource masterRes = getResourceFromCluster(masterClient, resource.getKind(), resource.getNamespace(), resource.getName());

            if (masterRes == null) {
                log.info("[Federation Sync] Missing in master. Cleaning members.");
                boolean deleted = deleteResourceFromMembers(members, resource);
                updateResourceStatus(resource, deleted ? "Success" : "Error", deleted ? "Synchronized (Deleted)" : "Delete failed");
                return deleted;
            }

            sanitizeResource(masterRes);
            Set<Dependency> dependencies = extractDependencies(masterClient, resource.getKind(), resource.getNamespace(), resource.getName());
            List<GenericKubernetesResource> depsToApply = new ArrayList<>();
            Map<String, String> dependencyYamls = new HashMap<>();

            for (Dependency dep : dependencies) {
                GenericKubernetesResource masterDep = getResourceFromCluster(masterClient, dep.getKind(), resource.getNamespace(), dep.getName());
                if (masterDep != null) {
                    sanitizeResource(masterDep);
                    depsToApply.add(masterDep);
                    dep.setStatus("Synced");
                    dependencyYamls.put(dep.getKind() + "/" + dep.getName(), k8sClientService.serializeToYaml(masterDep));
                } else {
                    dep.setStatus("Missing");
                }
            }

            try {
                resource.setBackupYaml(objectMapper.writeValueAsString(dependencyYamls));
                resource.setDependencyStatus(objectMapper.writeValueAsString(dependencies));
            } catch (Exception e) {
                log.warn("Metadata serialization error: {}", e.getMessage());
            }

            boolean resourceSuccess = true;
            StringBuilder errors = new StringBuilder();
            for (FederationMember member : members) {
                try {
                    KubernetesClient memberClient = k8sClientService.getClient(member.getMemberCluster().getUid());
                    ensureNamespaceExists(memberClient, resource.getNamespace());
                    for (GenericKubernetesResource dep : depsToApply) {
                        try {
                            applyResource(memberClient, dep, resource.getNamespace());
                        } catch (Exception e) {
                            errors.append(String.format("[%s] Dep %s: %s. ", member.getMemberCluster().getName(), dep.getMetadata().getName(), parseK8sError(e)));
                        }
                    }
                    try {
                        applyResource(memberClient, masterRes, resource.getNamespace());
                    } catch (Exception e) {
                        resourceSuccess = false;
                        errors.append(String.format("[%s] Error: %s. ", member.getMemberCluster().getName(), parseK8sError(e)));
                    }
                } catch (Exception e) {
                    resourceSuccess = false;
                    errors.append(String.format("[%s] Connect error: %s. ", member.getMemberCluster().getName(), e.getMessage()));
                }
            }
            updateResourceStatus(resource, resourceSuccess ? "Success" : "Error", errors.length() > 0 ? errors.toString() : null);
            return resourceSuccess;
        } catch (Exception e) {
            log.error("[Federation Sync] Error: {}", e.getMessage());
            updateResourceStatus(resource, "Error", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void sanitizeResource(GenericKubernetesResource res) {
        if (res == null || res.getMetadata() == null) return;
        res.getMetadata().setResourceVersion(null);
        res.getMetadata().setUid(null);
        res.getMetadata().setManagedFields(null);
        res.getMetadata().setCreationTimestamp(null);
        res.getMetadata().setGeneration(null);

        Object specObj = res.getAdditionalProperties().get("spec");
        if (!(specObj instanceof Map)) return;
        Map<String, Object> spec = (Map<String, Object>) specObj;

        String kind = res.getKind();

        // Service: clusterIP/clusterIPs are cluster-assigned — must be stripped
        if ("Service".equals(kind)) {
            spec.remove("clusterIP");
            spec.remove("clusterIPs");
        }

        // Strip status — never apply status block
        res.getAdditionalProperties().remove("status");
    }

    /**
     * Strip all known immutable fields for a given kind so that
     * server-side apply never fails on an existing resource.
     * Called BEFORE the retry attempt.
     */
    @SuppressWarnings("unchecked")
    private void stripImmutableFields(GenericKubernetesResource res) {
        Object specObj = res.getAdditionalProperties().get("spec");
        if (!(specObj instanceof Map)) return;
        Map<String, Object> spec = (Map<String, Object>) specObj;
        String kind = res.getKind();

        switch (kind) {
            case "Deployment", "DaemonSet", "ReplicaSet" -> {
                // spec.selector is immutable once created
                spec.remove("selector");
            }
            case "StatefulSet" -> {
                // spec.selector, spec.serviceName, spec.volumeClaimTemplates are all immutable
                spec.remove("selector");
                spec.remove("serviceName");
                spec.remove("volumeClaimTemplates");
            }
            case "Job" -> {
                // Job spec is almost entirely immutable — strip selector & template
                spec.remove("selector");
                spec.remove("template");
            }
            default -> {
                // Generic fallback: try stripping selector
                spec.remove("selector");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyResource(KubernetesClient client, GenericKubernetesResource res, String ns) {
        String targetNs = isClusterScoped(res.getKind()) ? null
                : (res.getMetadata().getNamespace() != null ? res.getMetadata().getNamespace() : ns);

        // Proactively check: if resource already exists on target, strip immutable fields before apply
        boolean existsOnTarget = false;
        try {
            GenericKubernetesResource existing = (targetNs != null)
                    ? client.genericKubernetesResources(res.getApiVersion(), res.getKind())
                            .inNamespace(targetNs).withName(res.getMetadata().getName()).get()
                    : client.genericKubernetesResources(res.getApiVersion(), res.getKind())
                            .withName(res.getMetadata().getName()).get();
            existsOnTarget = (existing != null);
        } catch (Exception ignored) { }

        if (existsOnTarget) {
            log.debug("[Federation] {}/{} already exists on target — stripping immutable fields before apply",
                    res.getKind(), res.getMetadata().getName());
            stripImmutableFields(res);
        }

        doServerSideApply(client, res, targetNs);
    }

    private void doServerSideApply(KubernetesClient client, GenericKubernetesResource res, String targetNs) {
        if (targetNs != null) {
            client.genericKubernetesResources(res.getApiVersion(), res.getKind()).inNamespace(targetNs).resource(res).fieldManager("poyraz-federation").forceConflicts().serverSideApply();
        } else {
            client.genericKubernetesResources(res.getApiVersion(), res.getKind()).resource(res).fieldManager("poyraz-federation").forceConflicts().serverSideApply();
        }
    }

    private boolean deleteResourceFromMembers(List<FederationMember> members, FederationResource resource) {
        boolean allDeleted = true;
        for (FederationMember member : members) {
            try {
                KubernetesClient client = k8sClientService.getClient(member.getMemberCluster().getUid());
                String apiVersion = getApiVersion(resource.getKind());
                if (isClusterScoped(resource.getKind())) {
                    client.genericKubernetesResources(apiVersion, resource.getKind()).withName(resource.getName()).delete();
                } else {
                    client.genericKubernetesResources(apiVersion, resource.getKind()).inNamespace(resource.getNamespace()).withName(resource.getName()).delete();
                }
            } catch (Exception e) {
                allDeleted = false;
            }
        }
        return allDeleted;
    }

    @Transactional
    public void deleteFederatedResourceCompletely(FederationResource resource, boolean fromMembers, boolean fromMaster) {
        List<FederationMember> members = federationMemberRepository.findByFederationId(resource.getFederation().getId());
        Set<Dependency> dependencies = new HashSet<>();
        try {
            if (resource.getDependencyStatus() != null) dependencies.addAll(List.of(objectMapper.readValue(resource.getDependencyStatus(), Dependency[].class)));
        } catch (Exception ignored) {}

        if (fromMembers) {
            deleteResourceFromMembers(members, resource);
            for (Dependency dep : dependencies) {
                if ("Namespace".equals(dep.getKind())) continue;
                for (FederationMember member : members) {
                    try {
                        KubernetesClient client = k8sClientService.getClient(member.getMemberCluster().getUid());
                        client.genericKubernetesResources(getApiVersion(dep.getKind()), dep.getKind()).inNamespace(resource.getNamespace()).withName(dep.getName()).delete();
                    } catch (Exception ignored) {}
                }
            }
        }

        if (fromMaster) {
            try {
                KubernetesClient master = k8sClientService.getClient(resource.getFederation().getMasterCluster().getUid());
                String apiVersion = getApiVersion(resource.getKind());
                if (isClusterScoped(resource.getKind())) {
                    master.genericKubernetesResources(apiVersion, resource.getKind()).withName(resource.getName()).delete();
                } else {
                    master.genericKubernetesResources(apiVersion, resource.getKind()).inNamespace(resource.getNamespace()).withName(resource.getName()).delete();
                }
            } catch (Exception e) {
                log.error("Master deletion error: {}", e.getMessage());
            }
        }
    }

    @Transactional
    public void rollbackResource(Long federationId, Long resourceId) {
        FederationResource resource = federationResourceRepository.findById(resourceId).orElseThrow(() -> new RuntimeException("Resource not found"));
        if (resource.getPreviousStateYaml() == null || resource.getPreviousStateYaml().isEmpty()) throw new RuntimeException("No previous state");
        List<FederationMember> members = federationMemberRepository.findByFederationId(federationId);
        try {
            for (FederationMember member : members) {
                KubernetesClient client = k8sClientService.getClient(member.getMemberCluster().getUid());
                List<HasMetadata> items = client.load(new java.io.ByteArrayInputStream(resource.getPreviousStateYaml().getBytes())).items();
                for (HasMetadata item : items) {
                    if (item.getMetadata() != null) {
                        item.getMetadata().setResourceVersion(null);
                        item.getMetadata().setUid(null);
                    }
                    client.resource(item).inNamespace(resource.getNamespace()).fieldManager("poyraz-federation").forceConflicts().serverSideApply();
                }
            }
            resource.setSyncStatus("Rollbacked");
            federationResourceRepository.save(resource);
            auditLogService.log("FEDERATION_ROLLBACK", "Rollback success for " + resource.getName());
        } catch (Exception e) {
            resource.setSyncStatus("Error");
            federationResourceRepository.save(resource);
            throw new RuntimeException("Rollback failed: " + e.getMessage());
        }
    }

    private GenericKubernetesResource getResourceFromCluster(KubernetesClient client, String kind, String namespace, String name) {
        String apiVersion = getApiVersion(kind);
        if (isClusterScoped(kind)) {
            return client.genericKubernetesResources(apiVersion, kind).withName(name).get();
        } else {
            return client.genericKubernetesResources(apiVersion, kind).inNamespace(namespace).withName(name).get();
        }
    }

    private String getApiVersion(String kind) {
        if (List.of("Deployment", "StatefulSet", "DaemonSet", "ReplicaSet").contains(kind)) return "apps/v1";
        if (List.of("CronJob", "Job").contains(kind)) return "batch/v1";
        if ("Ingress".equals(kind)) return "networking.k8s.io/v1";
        return "v1";
    }

    private boolean isClusterScoped(String kind) {
        return List.of("Namespace", "ClusterRole", "ClusterRoleBinding", "PersistentVolume").contains(kind);
    }

    private void updateResourceStatus(FederationResource resource, String status, String error) {
        resource.setSyncStatus(status);
        resource.setLastSyncTime(LocalDateTime.now());
        resource.setErrorMessage(error);
        if (error != null) resource.setLastErrorTime(LocalDateTime.now());
        federationResourceRepository.save(resource);
    }

    private void ensureNamespaceExists(KubernetesClient client, String ns) {
        if (client.namespaces().withName(ns).get() == null) {
            client.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(ns).endMetadata().build()).create();
        }
    }

    private void startNamespaceDependencyWatcher(String clusterUid, String ns, KubernetesClient client) {
        String key = clusterUid + ":" + ns;
        if (namespaceDependencyWatchers.containsKey(key)) return;
        List<Watch> watchers = new ArrayList<>();
        try {
            watchers.add(client.configMaps().inNamespace(ns).watch(new DependencyWatcher<>(ns, "ConfigMap")));
            watchers.add(client.secrets().inNamespace(ns).watch(new DependencyWatcher<>(ns, "Secret")));
            watchers.add(client.services().inNamespace(ns).watch(new DependencyWatcher<>(ns, "Service")));
            namespaceDependencyWatchers.put(key, watchers);
            log.info("[Federation Watcher] Namespace dependency watchers started for {}", ns);
        } catch (Exception e) {
            log.error("Namespace watcher failed for {}: {}", ns, e.getMessage());
        }
    }

    private class DependencyWatcher<T extends HasMetadata> implements Watcher<T> {
        private final String ns;
        private final String kind;
        public DependencyWatcher(String ns, String kind) { this.ns = ns; this.kind = kind; }
        @Override
        public void eventReceived(Action action, T res) {
            if (action == Action.MODIFIED || action == Action.DELETED) {
                log.info("[Federation Watcher] DEP CHANGE: {} {}/{}", kind, ns, res.getMetadata().getName());
                federationResourceRepository.findByNamespace(ns).forEach(fr -> self.triggerSyncForResource(fr.getId()));
            }
        }
        @Override public void onClose(WatcherException e) {}
    }

    private Set<Dependency> extractDependencies(KubernetesClient client, String kind, String ns, String name) throws Exception {
        Set<Dependency> deps = new HashSet<>();
        if (!"default".equals(ns)) deps.add(new Dependency("Namespace", ns));
        PodSpec spec = null;
        Map<String, String> labels = null;
        if ("Deployment".equals(kind)) {
            var d = client.apps().deployments().inNamespace(ns).withName(name).get();
            if (d != null && d.getSpec() != null) {
                spec = d.getSpec().getTemplate().getSpec();
                labels = d.getSpec().getTemplate().getMetadata().getLabels();
            }
        } else if ("StatefulSet".equals(kind)) {
            var s = client.apps().statefulSets().inNamespace(ns).withName(name).get();
            if (s != null && s.getSpec() != null) {
                spec = s.getSpec().getTemplate().getSpec();
                labels = s.getSpec().getTemplate().getMetadata().getLabels();
            }
        } else if ("DaemonSet".equals(kind)) {
            var ds = client.apps().daemonSets().inNamespace(ns).withName(name).get();
            if (ds != null && ds.getSpec() != null) {
                spec = ds.getSpec().getTemplate().getSpec();
                labels = ds.getSpec().getTemplate().getMetadata().getLabels();
            }
        }
        if (spec != null) {
            if (spec.getVolumes() != null) {
                for (var v : spec.getVolumes()) {
                    if (v.getConfigMap() != null) deps.add(new Dependency("ConfigMap", v.getConfigMap().getName()));
                    if (v.getSecret() != null) deps.add(new Dependency("Secret", v.getSecret().getSecretName()));
                }
            }
            if (spec.getContainers() != null) {
                for (var c : spec.getContainers()) extractEnvDeps(c, deps);
            }
        }
        if (labels != null && !labels.isEmpty()) {
            final Map<String, String> finalLabels = labels;
            client.services().inNamespace(ns).list().getItems().forEach(svc -> {
                var sel = svc.getSpec().getSelector();
                if (sel != null && !sel.isEmpty() && finalLabels.entrySet().containsAll(sel.entrySet())) {
                    deps.add(new Dependency("Service", svc.getMetadata().getName()));
                }
            });
        }
        return deps;
    }

    private void extractEnvDeps(Container c, Set<Dependency> deps) {
        if (c.getEnv() != null) {
            c.getEnv().forEach(e -> {
                if (e.getValueFrom() != null) {
                    if (e.getValueFrom().getConfigMapKeyRef() != null) deps.add(new Dependency("ConfigMap", e.getValueFrom().getConfigMapKeyRef().getName()));
                    if (e.getValueFrom().getSecretKeyRef() != null) deps.add(new Dependency("Secret", e.getValueFrom().getSecretKeyRef().getName()));
                }
            });
        }
    }

    private String parseK8sError(Exception e) {
        String m = e.getMessage();
        if (m == null) return "Unknown";
        if (m.contains("Conflict")) return "Conflict";
        return m;
    }
}
