package com.k8s.platform.controller.k8s;

import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.k8s.K8sClientService;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns only the workloads (Deployment / StatefulSet / DaemonSet) that
 * actually reference a given ConfigMap or Secret in their pod spec —
 * via volumes, envFrom, or env.valueFrom.
 *
 * GET /api/k8s/{clusterUid}/namespaces/{namespace}/related-workloads
 *   ?kind=ConfigMap|Secret
 *   &name=<resource-name>
 */
@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class WorkloadReferenceController {

    private final K8sClientService k8sClientService;
    private final ResourceAuthorizationHelper authHelper;

    @GetMapping("/namespaces/{namespace}/related-workloads")
    public List<Map<String, String>> relatedWorkloads(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestParam String kind,
            @RequestParam String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, kind, name, "view");

        KubernetesClient client = k8sClientService.getClient(clusterUid);
        List<Map<String, String>> results = new ArrayList<>();

        // Deployments
        client.apps().deployments().inNamespace(namespace).list().getItems()
                .stream()
                .filter(d -> referencesResource(d.getSpec().getTemplate().getSpec(), kind, name))
                .forEach(d -> results.add(workload("Deployment", d.getMetadata().getName())));

        // StatefulSets
        client.apps().statefulSets().inNamespace(namespace).list().getItems()
                .stream()
                .filter(s -> referencesResource(s.getSpec().getTemplate().getSpec(), kind, name))
                .forEach(s -> results.add(workload("StatefulSet", s.getMetadata().getName())));

        // DaemonSets
        client.apps().daemonSets().inNamespace(namespace).list().getItems()
                .stream()
                .filter(ds -> referencesResource(ds.getSpec().getTemplate().getSpec(), kind, name))
                .forEach(ds -> results.add(workload("DaemonSet", ds.getMetadata().getName())));

        return results;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> workload(String kind, String name) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("name", name);
        return m;
    }

    /**
     * Returns true when the given pod spec references {@code resourceName} of
     * type {@code kind} (ConfigMap or Secret) in any of:
     *   - volumes (configMap / secret)
     *   - containers + initContainers envFrom (configMapRef / secretRef)
     *   - containers + initContainers env.valueFrom (configMapKeyRef / secretKeyRef)
     */
    private boolean referencesResource(PodSpec spec, String kind, String resourceName) {
        if (spec == null) return false;
        boolean isConfigMap = "ConfigMap".equalsIgnoreCase(kind);

        // 1. Volumes
        if (spec.getVolumes() != null) {
            for (Volume v : spec.getVolumes()) {
                if (isConfigMap && v.getConfigMap() != null
                        && resourceName.equals(v.getConfigMap().getName())) return true;
                if (!isConfigMap && v.getSecret() != null
                        && resourceName.equals(v.getSecret().getSecretName())) return true;
            }
        }

        // 2. All containers (main + init)
        List<Container> allContainers = new ArrayList<>();
        if (spec.getContainers() != null)     allContainers.addAll(spec.getContainers());
        if (spec.getInitContainers() != null) allContainers.addAll(spec.getInitContainers());

        for (Container c : allContainers) {
            // envFrom
            if (c.getEnvFrom() != null) {
                for (EnvFromSource ef : c.getEnvFrom()) {
                    if (isConfigMap && ef.getConfigMapRef() != null
                            && resourceName.equals(ef.getConfigMapRef().getName())) return true;
                    if (!isConfigMap && ef.getSecretRef() != null
                            && resourceName.equals(ef.getSecretRef().getName())) return true;
                }
            }
            // env.valueFrom
            if (c.getEnv() != null) {
                for (EnvVar ev : c.getEnv()) {
                    if (ev.getValueFrom() == null) continue;
                    if (isConfigMap && ev.getValueFrom().getConfigMapKeyRef() != null
                            && resourceName.equals(ev.getValueFrom().getConfigMapKeyRef().getName())) return true;
                    if (!isConfigMap && ev.getValueFrom().getSecretKeyRef() != null
                            && resourceName.equals(ev.getValueFrom().getSecretKeyRef().getName())) return true;
                }
            }
        }

        return false;
    }
}
