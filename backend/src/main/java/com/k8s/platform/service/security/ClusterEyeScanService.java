package com.k8s.platform.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.security.ClusterEyeResult;
import com.k8s.platform.domain.repository.security.ClusterEyeResultRepository;
import com.k8s.platform.service.k8s.K8sClientService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ClusterEye — multi-workload security scanner.
 *
 * Scans Deployments, StatefulSets, DaemonSets, CronJobs, and Pods for:
 *   • SecurityContext issues (root, privilege escalation, dangerous capabilities)
 *   • :latest image tags
 *   • Missing resource limits/requests
 *   • Plaintext secrets in env vars
 *   • Missing liveness/readiness probes
 *   • HostPath volumes
 *   • Default ServiceAccount usage
 *   • Missing NetworkPolicies
 *
 * Results are UPSERTED per (clusterUid, namespace, workloadKind, workloadName):
 * same workload → existing row updated, not duplicated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterEyeScanService {

    private final K8sClientService k8sClientService;
    private final ClusterEyeResultRepository repository;
    private final ObjectMapper objectMapper;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional
    public int scanCluster(String clusterUid) {
        log.info("[ClusterEye] Starting scan for cluster: {}", clusterUid);
        KubernetesClient client = k8sClientService.getClient(clusterUid);
        int scanned = 0;
        try {
            scanned += scanDeployments(clusterUid, client);
            scanned += scanStatefulSets(clusterUid, client);
            scanned += scanDaemonSets(clusterUid, client);
            scanned += scanCronJobs(clusterUid, client);
            scanned += scanPods(clusterUid, client);
        } catch (Exception e) {
            log.error("[ClusterEye] Scan failed for cluster {}: {}", clusterUid, e.getMessage(), e);
        }
        log.info("[ClusterEye] Scan complete for cluster {} — {} workloads processed", clusterUid, scanned);
        return scanned;
    }

    // ── Per-kind scanners ─────────────────────────────────────────────────────

    private int scanDeployments(String uid, KubernetesClient client) {
        int n = 0;
        for (Deployment d : client.apps().deployments().inAnyNamespace().list().getItems()) {
            upsert(uid, ns(d), "Deployment", d.getMetadata().getName(),
                   scanPodSpec(d.getSpec().getTemplate().getSpec(),
                               d.getMetadata().getName(), uid, ns(d), "Deployment", client));
            n++;
        }
        return n;
    }

    private int scanStatefulSets(String uid, KubernetesClient client) {
        int n = 0;
        for (StatefulSet s : client.apps().statefulSets().inAnyNamespace().list().getItems()) {
            upsert(uid, ns(s), "StatefulSet", s.getMetadata().getName(),
                   scanPodSpec(s.getSpec().getTemplate().getSpec(),
                               s.getMetadata().getName(), uid, ns(s), "StatefulSet", client));
            n++;
        }
        return n;
    }

    private int scanDaemonSets(String uid, KubernetesClient client) {
        int n = 0;
        for (DaemonSet d : client.apps().daemonSets().inAnyNamespace().list().getItems()) {
            upsert(uid, ns(d), "DaemonSet", d.getMetadata().getName(),
                   scanPodSpec(d.getSpec().getTemplate().getSpec(),
                               d.getMetadata().getName(), uid, ns(d), "DaemonSet", client));
            n++;
        }
        return n;
    }

    private int scanCronJobs(String uid, KubernetesClient client) {
        int n = 0;
        for (CronJob cj : client.batch().v1().cronjobs().inAnyNamespace().list().getItems()) {
            upsert(uid, ns(cj), "CronJob", cj.getMetadata().getName(),
                   scanPodSpec(cj.getSpec().getJobTemplate().getSpec().getTemplate().getSpec(),
                               cj.getMetadata().getName(), uid, ns(cj), "CronJob", client));
            n++;
        }
        return n;
    }

    private int scanPods(String uid, KubernetesClient client) {
        int n = 0;
        for (Pod p : client.pods().inAnyNamespace().list().getItems()) {
            if (isManagedPod(p)) continue; // skip pods owned by Deployments etc.
            upsert(uid, ns(p), "Pod", p.getMetadata().getName(),
                   scanPodSpec(p.getSpec(), p.getMetadata().getName(), uid, ns(p), "Pod", client));
            n++;
        }
        return n;
    }

    // ── Core scanner ──────────────────────────────────────────────────────────

    List<String> scanPodSpec(PodSpec spec, String name, String clusterUid,
                             String namespace, String kind, KubernetesClient client) {
        List<String> findings = new ArrayList<>();
        if (spec == null) return findings;

        List<Container> allContainers = new ArrayList<>();
        if (spec.getContainers() != null)     allContainers.addAll(spec.getContainers());
        if (spec.getInitContainers() != null) allContainers.addAll(spec.getInitContainers());

        checkNetworkPolicies(findings, namespace, client);
        checkServiceAccount(findings, spec);

        for (Container c : allContainers) {
            checkSecurityContext(findings, c, spec);
            checkImageTag(findings, c);
            checkResources(findings, c);
            checkEnvSecrets(findings, c);
            checkProbes(findings, c, kind);
        }
        checkHostPathVolumes(findings, spec);
        return findings;
    }

    // ── Individual checks ─────────────────────────────────────────────────────

    private void checkNetworkPolicies(List<String> f, String ns, KubernetesClient client) {
        try {
            var policies = client.network().networkPolicies().inNamespace(ns).list().getItems();
            if (policies.isEmpty())
                f.add("[HIGH] No NetworkPolicy found in namespace '" + ns + "' — unrestricted traffic allowed");
        } catch (Exception ignored) {}
    }

    private void checkServiceAccount(List<String> f, PodSpec spec) {
        String sa = spec.getServiceAccountName();
        if (sa == null || sa.isBlank() || "default".equals(sa))
            f.add("[MEDIUM] Using default ServiceAccount — create a dedicated service account");
    }

    private void checkSecurityContext(List<String> f, Container c, PodSpec spec) {
        SecurityContext sc = c.getSecurityContext();
        if (sc != null) {
            if (Boolean.TRUE.equals(sc.getPrivileged()))
                f.add("[CRITICAL] Container '" + c.getName() + "' runs in privileged mode");
            if (Boolean.TRUE.equals(sc.getAllowPrivilegeEscalation()))
                f.add("[HIGH] Container '" + c.getName() + "': allowPrivilegeEscalation=true");
            if (sc.getRunAsNonRoot() == null || !Boolean.TRUE.equals(sc.getRunAsNonRoot()))
                f.add("[HIGH] Container '" + c.getName() + "': runAsNonRoot not enforced");
            if (sc.getCapabilities() != null && sc.getCapabilities().getAdd() != null) {
                List<String> dangerous = Arrays.asList("SYS_ADMIN","NET_ADMIN","ALL","SYS_PTRACE","SYS_MODULE");
                for (var cap : sc.getCapabilities().getAdd()) {
                    if (dangerous.contains(cap.toString()))
                        f.add("[CRITICAL] Container '" + c.getName() + "': dangerous capability " + cap);
                }
            }
            if (sc.getReadOnlyRootFilesystem() == null || !Boolean.TRUE.equals(sc.getReadOnlyRootFilesystem()))
                f.add("[MEDIUM] Container '" + c.getName() + "': readOnlyRootFilesystem not set");
        } else {
            f.add("[HIGH] Container '" + c.getName() + "': no SecurityContext defined");
        }

        if (spec.getSecurityContext() == null)
            f.add("[MEDIUM] No pod-level SecurityContext defined");
    }

    private void checkImageTag(List<String> f, Container c) {
        String image = c.getImage();
        if (image != null && (image.endsWith(":latest") || !image.contains(":")))
            f.add("[HIGH] Container '" + c.getName() + "': using ':latest' or untagged image — " + image);
    }

    private void checkResources(List<String> f, Container c) {
        var res = c.getResources();
        if (res == null || res.getLimits() == null || res.getLimits().isEmpty())
            f.add("[HIGH] Container '" + c.getName() + "': no resource limits defined");
        if (res == null || res.getRequests() == null || res.getRequests().isEmpty())
            f.add("[MEDIUM] Container '" + c.getName() + "': no resource requests defined");
    }

    private static final List<String> SECRET_KEYWORDS =
            Arrays.asList("password","passwd","secret","token","api_key","apikey","private_key","db_pass","database_password");

    private void checkEnvSecrets(List<String> f, Container c) {
        if (c.getEnv() == null) return;
        for (EnvVar ev : c.getEnv()) {
            String key = ev.getName() != null ? ev.getName().toLowerCase() : "";
            if (SECRET_KEYWORDS.stream().anyMatch(key::contains) && ev.getValue() != null && !ev.getValue().isBlank())
                f.add("[CRITICAL] Container '" + c.getName() + "': possible plaintext secret in env var '" + ev.getName() + "'");
        }
    }

    private void checkProbes(List<String> f, Container c, String kind) {
        if ("CronJob".equals(kind) || "Pod".equals(kind)) return;
        if (c.getLivenessProbe() == null)
            f.add("[MEDIUM] Container '" + c.getName() + "': no liveness probe");
        if (c.getReadinessProbe() == null)
            f.add("[MEDIUM] Container '" + c.getName() + "': no readiness probe");
    }

    private void checkHostPathVolumes(List<String> f, PodSpec spec) {
        if (spec.getVolumes() == null) return;
        for (Volume v : spec.getVolumes()) {
            if (v.getHostPath() != null)
                f.add("[HIGH] Volume '" + v.getName() + "': uses hostPath (" + v.getHostPath().getPath() + ") — host access risk");
        }
    }

    // ── Upsert ────────────────────────────────────────────────────────────────

    private void upsert(String clusterUid, String namespace, String kind, String name,
                        List<String> findings) {
        try {
            int critical = (int) findings.stream().filter(f -> f.startsWith("[CRITICAL]")).count();
            int high     = (int) findings.stream().filter(f -> f.startsWith("[HIGH]")).count();
            int medium   = (int) findings.stream().filter(f -> f.startsWith("[MEDIUM]")).count();
            String json  = objectMapper.writeValueAsString(findings);
            LocalDateTime now = LocalDateTime.now();

            repository.findByClusterUidAndNamespaceAndWorkloadKindAndWorkloadName(
                    clusterUid, namespace, kind, name)
                .ifPresentOrElse(existing -> {
                    existing.setFindings(json);
                    existing.setCriticalCount(critical);
                    existing.setHighCount(high);
                    existing.setMediumCount(medium);
                    existing.setTotalCount(findings.size());
                    existing.setLastScannedAt(now);
                    repository.save(existing);
                }, () -> repository.save(ClusterEyeResult.builder()
                    .clusterUid(clusterUid).namespace(namespace)
                    .workloadKind(kind).workloadName(name)
                    .findings(json).criticalCount(critical)
                    .highCount(high).mediumCount(medium)
                    .totalCount(findings.size()).lastScannedAt(now)
                    .build()));
        } catch (Exception e) {
            log.warn("[ClusterEye] Failed to upsert result for {}/{}/{}: {}", clusterUid, kind, name, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String ns(HasMetadata r) {
        String ns = r.getMetadata().getNamespace();
        return ns != null ? ns : "default";
    }

    private boolean isManagedPod(Pod p) {
        return p.getMetadata().getOwnerReferences() != null && !p.getMetadata().getOwnerReferences().isEmpty();
    }
}
