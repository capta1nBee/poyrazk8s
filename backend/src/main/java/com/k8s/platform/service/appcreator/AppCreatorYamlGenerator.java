package com.k8s.platform.service.appcreator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generates Kubernetes YAML manifests from AppCreator wizard config (stored as JSON).
 * Config shape mirrors the frontend wizard state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppCreatorYamlGenerator {

    private final ObjectMapper objectMapper;

    public Map<String, String> generate(String configJson) {
        try {
            Map<String, Object> cfg = objectMapper.readValue(configJson, new TypeReference<>() {});
            Map<String, String> files = new LinkedHashMap<>();

            String name = str(cfg, "name");
            String namespace = str(cfg, "namespace");
            String workloadType = str(cfg, "workloadType", "Deployment");
            String image = str(cfg, "image", "nginx:latest");
            int replicas = num(cfg, "replicas", 1);
            List<Map<String, Object>> ports = listOf(cfg, "ports");
            List<Map<String, Object>> envVars = listOf(cfg, "envVars");
            Map<String, Object> resources = mapOf(cfg, "resources");
            Map<String, Object> probes = mapOf(cfg, "probes");
            List<Map<String, Object>> volumes = listOf(cfg, "volumes");
            List<Map<String, Object>> volumeMounts = listOf(cfg, "volumeMounts");

            boolean createService = bool(cfg, "createService");
            Map<String, Object> serviceConfig = mapOf(cfg, "serviceConfig");
            boolean createIngress = bool(cfg, "createIngress");
            Map<String, Object> ingressCfg = mapOf(cfg, "ingressConfig");
            // Legacy fallbacks
            String ingressHost = ingressCfg.isEmpty() ? str(cfg, "ingressHost") : str(ingressCfg, "host");
            String ingressPath = ingressCfg.isEmpty() ? str(cfg, "ingressPath", "/") : str(ingressCfg, "path", "/");
            String ingressClass = ingressCfg.isEmpty() ? str(cfg, "ingressClass", "nginx") : str(ingressCfg, "ingressClass", "nginx");
            String ingressPathType = ingressCfg.isEmpty() ? "Prefix" : str(ingressCfg, "pathType", "Prefix");
            boolean tlsEnabled = !ingressCfg.isEmpty() && bool(ingressCfg, "tlsEnabled");
            String tlsSecret = ingressCfg.isEmpty() ? "" : str(ingressCfg, "tlsSecret");
            @SuppressWarnings("unchecked")
            Map<String, Object> ingressAnnotations = ingressCfg.isEmpty() ? Collections.emptyMap()
                    : (ingressCfg.get("annotations") instanceof Map ? (Map<String, Object>) ingressCfg.get("annotations") : Collections.emptyMap());

            Map<String, Object> configMapData = mapOf(cfg, "configMap");
            Map<String, Object> secretData = mapOf(cfg, "secret");
            List<Map<String, Object>> configMaps = listOf(cfg, "configMaps");
            List<Map<String, Object>> secrets = listOf(cfg, "secrets");
            Map<String, Object> hpa = mapOf(cfg, "hpa");

            files.put(workloadType.toLowerCase() + ".yaml",
                    generateWorkload(name, namespace, workloadType, image, replicas, ports, envVars, resources, probes, volumes, volumeMounts, cfg));

            if (createService) {
                String svcType = serviceConfig.isEmpty() ? "ClusterIP" : str(serviceConfig, "type", "ClusterIP");
                files.put("service.yaml", generateService(name, namespace, ports, svcType));
            }

            if (createIngress && createService && ingressHost != null && !ingressHost.isBlank()) {
                int servicePort = ports.isEmpty() ? 80 : num(ports.get(0), "containerPort", 80);
                files.put("ingress.yaml", generateIngress(name, namespace, ingressHost, ingressPath, ingressPathType, ingressClass, servicePort, tlsEnabled, tlsSecret, ingressAnnotations));
            }

            // Legacy configmap/secret
            if (!configMapData.isEmpty()) files.put("configmap.yaml", generateConfigMap(name, namespace, configMapData));
            if (!secretData.isEmpty()) files.put("secret.yaml", generateSecret(name, namespace, secretData));

            // New multi-configmap support
            for (int i = 0; i < configMaps.size(); i++) {
                Map<String, Object> cm = configMaps.get(i);
                String cmName = str(cm, "name", name + "-config-" + i);
                Map<String, Object> cmData = mapOf(cm, "data");
                if (!cmData.isEmpty()) files.put("configmap-" + cmName + ".yaml", generateConfigMap(cmName, namespace, cmData));
            }
            for (int i = 0; i < secrets.size(); i++) {
                Map<String, Object> sec = secrets.get(i);
                String secName = str(sec, "name", name + "-secret-" + i);
                Map<String, Object> secData = mapOf(sec, "data");
                if (!secData.isEmpty()) files.put("secret-" + secName + ".yaml", generateSecret(secName, namespace, secData));
            }

            // HPA
            if (!hpa.isEmpty() && bool(hpa, "enabled")) {
                files.put("hpa.yaml", generateHpa(name, namespace, workloadType, hpa));
            }

            return files;
        } catch (Exception e) {
            log.error("YAML generation failed", e);
            throw new RuntimeException("YAML generation failed: " + e.getMessage(), e);
        }
    }

    private String generateWorkload(String name, String ns, String kind, String image,
            int replicas, List<Map<String, Object>> ports,
            List<Map<String, Object>> envVars, Map<String, Object> resources,
            Map<String, Object> probes, List<Map<String, Object>> volumes,
            List<Map<String, Object>> volumeMounts, Map<String, Object> cfg) {
        StringBuilder sb = new StringBuilder();
        boolean isCronJob = "CronJob".equals(kind);
        String apiVersion = "batch/v1".equals(kind) || isCronJob ? "batch/v1" : "apps/v1";

        if (isCronJob) {
            sb.append("apiVersion: batch/v1\nkind: CronJob\nmetadata:\n  name: ").append(name).append("\n  namespace: ").append(ns).append("\n");
            sb.append("spec:\n  schedule: \"").append(str(cfg, "cronJobSchedule", "*/5 * * * *")).append("\"\n");
            sb.append("  concurrencyPolicy: ").append(str(cfg, "cronJobConcurrencyPolicy", "Allow")).append("\n");
            sb.append("  successfulJobsHistoryLimit: ").append(num(cfg, "cronJobSuccessHistory", 3)).append("\n");
            sb.append("  failedJobsHistoryLimit: ").append(num(cfg, "cronJobFailedHistory", 1)).append("\n");
            sb.append("  jobTemplate:\n    spec:\n      template:\n        metadata:\n          labels:\n            app: ").append(name).append("\n");
            sb.append("        spec:\n          restartPolicy: OnFailure\n          containers:\n          - name: ").append(name).append("\n");
            sb.append("            image: ").append(image).append("\n");
            appendContainerBody(sb, ports, envVars, resources, probes, volumeMounts, "            ");
            appendVolumes(sb, volumes, "        ");
            return sb.toString();
        }

        sb.append("apiVersion: apps/v1\nkind: ").append(kind).append("\nmetadata:\n  name: ").append(name).append("\n  namespace: ").append(ns).append("\n");
        sb.append("spec:\n");

        if ("DaemonSet".equals(kind)) {
            // DaemonSet has no replicas
        } else {
            sb.append("  replicas: ").append(replicas).append("\n");
        }

        // Strategy
        if ("Deployment".equals(kind)) {
            String strategy = str(cfg, "deploymentStrategy", "RollingUpdate");
            sb.append("  strategy:\n    type: ").append(strategy).append("\n");
            if ("RollingUpdate".equals(strategy)) {
                sb.append("    rollingUpdate:\n      maxSurge: ").append(str(cfg, "maxSurge", "25%"))
                  .append("\n      maxUnavailable: ").append(str(cfg, "maxUnavailable", "25%")).append("\n");
            }
        }

        sb.append("  selector:\n    matchLabels:\n      app: ").append(name).append("\n");

        if ("StatefulSet".equals(kind)) {
            String svcName = str(cfg, "statefulSetServiceName");
            if (!svcName.isBlank()) sb.append("  serviceName: \"").append(svcName).append("\"\n");
            sb.append("  podManagementPolicy: ").append(str(cfg, "statefulSetPodManagementPolicy", "OrderedReady")).append("\n");
        }

        sb.append("  template:\n    metadata:\n      labels:\n        app: ").append(name).append("\n");
        sb.append("    spec:\n");

        // NodeSelector for DaemonSet
        Map<String, Object> nodeSelector = mapOf(cfg, "daemonSetNodeSelector");
        if (!nodeSelector.isEmpty()) {
            sb.append("      nodeSelector:\n");
            nodeSelector.forEach((k, v) -> sb.append("        ").append(k).append(": ").append(v).append("\n"));
        }

        sb.append("      containers:\n      - name: ").append(name).append("\n        image: ").append(image).append("\n");
        String pullPolicy = str(cfg, "imagePullPolicy", "IfNotPresent");
        sb.append("        imagePullPolicy: ").append(pullPolicy).append("\n");
        appendContainerBody(sb, ports, envVars, resources, probes, volumeMounts, "        ");
        appendVolumes(sb, volumes, "    ");
        return sb.toString();
    }

    private void appendContainerBody(StringBuilder sb, List<Map<String, Object>> ports,
            List<Map<String, Object>> envVars, Map<String, Object> resources,
            Map<String, Object> probes, List<Map<String, Object>> volumeMounts, String indent) {
        if (!ports.isEmpty()) {
            sb.append(indent).append("ports:\n");
            for (Map<String, Object> p : ports) {
                sb.append(indent).append("- containerPort: ").append(p.getOrDefault("containerPort", 80)).append("\n");
                if (p.containsKey("name")) sb.append(indent).append("  name: ").append(p.get("name")).append("\n");
            }
        }
        if (!envVars.isEmpty()) {
            sb.append(indent).append("env:\n");
            for (Map<String, Object> e : envVars) {
                sb.append(indent).append("- name: ").append(e.getOrDefault("name", "")).append("\n");
                sb.append(indent).append("  value: \"").append(e.getOrDefault("value", "")).append("\"\n");
            }
        }
        if (!resources.isEmpty()) {
            sb.append(indent).append("resources:\n");
            if (resources.containsKey("requests")) {
                sb.append(indent).append("  requests:\n");
                Map<?, ?> req = (Map<?, ?>) resources.get("requests");
                req.forEach((k, v) -> sb.append(indent).append("    ").append(k).append(": ").append(v).append("\n"));
            }
            if (resources.containsKey("limits")) {
                sb.append(indent).append("  limits:\n");
                Map<?, ?> lim = (Map<?, ?>) resources.get("limits");
                lim.forEach((k, v) -> sb.append(indent).append("    ").append(k).append(": ").append(v).append("\n"));
            }
        }
        // Probes
        if (!probes.isEmpty()) {
            appendProbe(sb, mapOf(probes, "liveness"), "livenessProbe", indent);
            appendProbe(sb, mapOf(probes, "readiness"), "readinessProbe", indent);
        }
        // Volume mounts
        if (!volumeMounts.isEmpty()) {
            sb.append(indent).append("volumeMounts:\n");
            for (Map<String, Object> vm : volumeMounts) {
                sb.append(indent).append("- name: ").append(str(vm, "name")).append("\n");
                sb.append(indent).append("  mountPath: ").append(str(vm, "mountPath", "/data")).append("\n");
                if (bool(vm, "readOnly")) sb.append(indent).append("  readOnly: true\n");
                String subPath = str(vm, "subPath");
                if (!subPath.isBlank()) sb.append(indent).append("  subPath: ").append(subPath).append("\n");
            }
        }
    }

    private void appendProbe(StringBuilder sb, Map<String, Object> probe, String probeKey, String indent) {
        if (probe.isEmpty() || !bool(probe, "enabled")) return;
        String type = str(probe, "type", "http");
        sb.append(indent).append(probeKey).append(":\n");
        if ("http".equals(type)) {
            sb.append(indent).append("  httpGet:\n");
            sb.append(indent).append("    path: ").append(str(probe, "path", "/")).append("\n");
            sb.append(indent).append("    port: ").append(num(probe, "port", 80)).append("\n");
        } else if ("tcp".equals(type)) {
            sb.append(indent).append("  tcpSocket:\n");
            sb.append(indent).append("    port: ").append(num(probe, "port", 80)).append("\n");
        } else {
            sb.append(indent).append("  exec:\n    command:\n    - ").append(str(probe, "command", "/healthcheck")).append("\n");
        }
        sb.append(indent).append("  initialDelaySeconds: ").append(num(probe, "initialDelay", 10)).append("\n");
        sb.append(indent).append("  periodSeconds: ").append(num(probe, "period", 10)).append("\n");
        sb.append(indent).append("  failureThreshold: ").append(num(probe, "failureThreshold", 3)).append("\n");
    }

    private void appendVolumes(StringBuilder sb, List<Map<String, Object>> volumes, String indent) {
        if (volumes.isEmpty()) return;
        sb.append(indent).append("  volumes:\n");
        for (Map<String, Object> vol : volumes) {
            String volName = str(vol, "name");
            String volType = str(vol, "type", "emptyDir");
            sb.append(indent).append("  - name: ").append(volName).append("\n");
            switch (volType) {
                case "pvc" -> {
                    String claimName = str(vol, "claimName");
                    sb.append(indent).append("    persistentVolumeClaim:\n")
                      .append(indent).append("      claimName: ").append(claimName).append("\n");
                }
                case "configMap" -> {
                    sb.append(indent).append("    configMap:\n")
                      .append(indent).append("      name: ").append(str(vol, "configMapName", volName)).append("\n");
                }
                case "secret" -> {
                    sb.append(indent).append("    secret:\n")
                      .append(indent).append("      secretName: ").append(str(vol, "secretName", volName)).append("\n");
                }
                case "hostPath" -> {
                    sb.append(indent).append("    hostPath:\n")
                      .append(indent).append("      path: ").append(str(vol, "hostPath", "/tmp")).append("\n");
                }
                default -> sb.append(indent).append("    emptyDir: {}\n");
            }
        }
    }

    private String generateService(String name, String ns, List<Map<String, Object>> ports, String svcType) {
        StringBuilder sb = new StringBuilder();
        sb.append("apiVersion: v1\nkind: Service\nmetadata:\n  name: ").append(name)
                .append("\n  namespace: ").append(ns).append("\nspec:\n  type: ").append(svcType)
                .append("\n  selector:\n    app: ").append(name).append("\n  ports:\n");
        for (Map<String, Object> p : ports) {
            int cp = num(p, "containerPort", 80);
            String portName = str(p, "name", "http");
            sb.append("  - name: ").append(portName).append("\n    port: ").append(cp)
              .append("\n    targetPort: ").append(cp).append("\n");
            if ("NodePort".equals(svcType) && p.containsKey("nodePort")) {
                sb.append("    nodePort: ").append(num(p, "nodePort", 30000)).append("\n");
            }
        }
        return sb.toString();
    }

    private String generateIngress(String name, String ns, String host, String path, String pathType,
            String ingressClass, int servicePort, boolean tlsEnabled, String tlsSecret,
            Map<String, Object> extraAnnotations) {
        StringBuilder sb = new StringBuilder();
        sb.append("apiVersion: networking.k8s.io/v1\nkind: Ingress\nmetadata:\n  name: ").append(name)
          .append("\n  namespace: ").append(ns).append("\n");

        // Annotations block (always present for backward compat + extra annotations)
        sb.append("  annotations:\n");
        if (ingressClass != null && !ingressClass.isBlank()) {
            // Deprecated annotation kept for backward compatibility
            sb.append("    kubernetes.io/ingress.class: \"").append(ingressClass).append("\"\n");
        }
        if (extraAnnotations != null) {
            extraAnnotations.forEach((k, v) -> sb.append("    ").append(k).append(": \"").append(v).append("\"\n"));
        }

        sb.append("spec:\n");
        // Modern ingressClassName field (Kubernetes >= 1.18)
        if (ingressClass != null && !ingressClass.isBlank()) {
            sb.append("  ingressClassName: ").append(ingressClass).append("\n");
        }
        if (tlsEnabled && tlsSecret != null && !tlsSecret.isBlank()) {
            sb.append("  tls:\n  - hosts:\n    - ").append(host)
              .append("\n    secretName: ").append(tlsSecret).append("\n");
        }
        String resolvedPathType = (pathType != null && !pathType.isBlank()) ? pathType : "Prefix";
        sb.append("  rules:\n  - host: ").append(host)
          .append("\n    http:\n      paths:\n      - path: ").append(path)
          .append("\n        pathType: ").append(resolvedPathType)
          .append("\n        backend:\n          service:\n")
          .append("            name: ").append(name)
          .append("\n            port:\n              number: ").append(servicePort).append("\n");
        return sb.toString();
    }

    private String generateHpa(String name, String ns, String workloadKind, Map<String, Object> hpa) {
        int minReplicas = num(hpa, "minReplicas", 1);
        int maxReplicas = num(hpa, "maxReplicas", 5);
        int targetCpu = num(hpa, "targetCPU", 80);
        int targetMemory = num(hpa, "targetMemory", 0);

        StringBuilder sb = new StringBuilder();
        sb.append("apiVersion: autoscaling/v2\nkind: HorizontalPodAutoscaler\nmetadata:\n  name: ")
          .append(name).append("-hpa\n  namespace: ").append(ns).append("\n");
        sb.append("spec:\n  scaleTargetRef:\n    apiVersion: apps/v1\n    kind: ").append(workloadKind)
          .append("\n    name: ").append(name).append("\n");
        sb.append("  minReplicas: ").append(minReplicas).append("\n");
        sb.append("  maxReplicas: ").append(maxReplicas).append("\n");
        sb.append("  metrics:\n");
        if (targetCpu > 0) {
            sb.append("  - type: Resource\n    resource:\n      name: cpu\n")
              .append("      target:\n        type: Utilization\n        averageUtilization: ").append(targetCpu).append("\n");
        }
        if (targetMemory > 0) {
            sb.append("  - type: Resource\n    resource:\n      name: memory\n")
              .append("      target:\n        type: Utilization\n        averageUtilization: ").append(targetMemory).append("\n");
        }
        return sb.toString();
    }

    private String generateConfigMap(String name, String ns, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: ").append(name)
                .append("\n  namespace: ").append(ns).append("\ndata:\n");
        data.forEach((k, v) -> sb.append("  ").append(k).append(": \"").append(v).append("\"\n"));
        return sb.toString();
    }

    private String generateSecret(String name, String ns, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("apiVersion: v1\nkind: Secret\nmetadata:\n  name: ").append(name)
                .append("\n  namespace: ").append(ns).append("\ntype: Opaque\nstringData:\n");
        data.forEach((k, v) -> sb.append("  ").append(k).append(": \"").append(v).append("\"\n"));
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(Map<String, Object> cfg, String key) {
        Object val = cfg.get(key);
        if (val instanceof List) return (List<Map<String, Object>>) val;
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Map<String, Object> cfg, String key) {
        Object val = cfg.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return Collections.emptyMap();
    }

    private String str(Map<String, Object> cfg, String key) { return str(cfg, key, ""); }
    private String str(Map<String, Object> cfg, String key, String def) {
        Object v = cfg.get(key); return v != null ? v.toString() : def;
    }
    private int num(Map<String, Object> cfg, String key, int def) {
        Object v = cfg.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) try { return Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
        return def;
    }
    private boolean bool(Map<String, Object> cfg, String key) {
        Object v = cfg.get(key); return Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
    }
}

