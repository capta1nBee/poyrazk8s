package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.k8s.K8sClientService;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetricsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns cluster-wide CPU and Memory totals computed from node allocatable
 * resources, alongside the current usage percentage stored in the Cluster entity.
 *
 * GET /api/k8s/{clusterUid}/cluster-health
 *
 * Response:
 * {
 *   "cpuPct": 45.0,
 *   "memPct": 32.0,
 *   "cpuUsedCores": 2.25,
 *   "cpuTotalCores": 5.0,
 *   "memUsedGb": 3.2,
 *   "memTotalGb": 10.0,
 *   "cpuLabel": "2.25 / 5.00 cores",
 *   "memLabel": "3.2 / 10.0 GB"
 * }
 */
@Slf4j
@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class ClusterHealthController {

    private final K8sClientService k8sClientService;
    private final ClusterRepository clusterRepository;
    private final ResourceAuthorizationHelper authHelper;

    @GetMapping("/cluster-health")
    public Map<String, Object> clusterHealth(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "dashboard");

        // ── 1. Compute allocatable totals from live K8s node data ─────────────
        double cpuTotalCores = 0, memTotalGb = 0;
        double cpuUsedCores = 0, memUsedGb = 0;
        KubernetesClient client = null;
        try {
            client = k8sClientService.getClient(clusterUid);
            List<Node> nodes = client.nodes().list().getItems();
            for (Node node : nodes) {
                if (node.getStatus() == null || node.getStatus().getAllocatable() == null) continue;
                var alloc = node.getStatus().getAllocatable();

                String cpuStr = alloc.getOrDefault("cpu", null) != null
                        ? alloc.get("cpu").toString() : null;
                if (cpuStr != null) cpuTotalCores += parseMilliCores(cpuStr);

                String memStr = alloc.getOrDefault("memory", null) != null
                        ? alloc.get("memory").toString() : null;
                if (memStr != null) memTotalGb += parseMiToGb(memStr);
            }
        } catch (Exception e) {
            log.warn("[ClusterHealth] Failed to fetch node allocatable for {}: {}", clusterUid, e.getMessage());
        }

        // ── 2. Get REAL usage from metrics-server (kubectl top nodes equivalent) ──
        try {
            if (client == null) client = k8sClientService.getClient(clusterUid);
            NodeMetricsList metricsList = client.top().nodes().metrics();
            if (metricsList != null && metricsList.getItems() != null) {
                for (NodeMetrics nm : metricsList.getItems()) {
                    if (nm.getUsage() == null) continue;

                    String cpuUsage = nm.getUsage().getOrDefault("cpu", null) != null
                            ? nm.getUsage().get("cpu").toString() : null;
                    if (cpuUsage != null) cpuUsedCores += parseMilliCores(cpuUsage);

                    String memUsage = nm.getUsage().getOrDefault("memory", null) != null
                            ? nm.getUsage().get("memory").toString() : null;
                    if (memUsage != null) memUsedGb += parseMiToGb(memUsage);
                }
            }
        } catch (Exception e) {
            log.warn("[ClusterHealth] metrics-server unavailable for {}: {} — falling back to stored percentage",
                    clusterUid, e.getMessage());
            // Fallback: use stored cluster entity percentage
            var clusterOpt = clusterRepository.findByUid(clusterUid);
            if (clusterOpt.isPresent()) {
                double cpuPctStored = parsePercent(clusterOpt.get().getCpu());
                double memPctStored = parsePercent(clusterOpt.get().getMemory());
                cpuUsedCores = cpuTotalCores * cpuPctStored / 100.0;
                memUsedGb    = memTotalGb * memPctStored / 100.0;
            }
        }

        // ── 3. Compute percentages from real usage ──────────────────────────────
        double cpuPct = cpuTotalCores > 0 ? round2(cpuUsedCores / cpuTotalCores * 100.0) : 0;
        double memPct = memTotalGb    > 0 ? round2(memUsedGb    / memTotalGb    * 100.0) : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cpuPct",        cpuPct);
        result.put("memPct",        memPct);
        result.put("cpuTotalCores", round2(cpuTotalCores));
        result.put("cpuUsedCores",  round2(cpuUsedCores));
        result.put("memTotalGb",    round2(memTotalGb));
        result.put("memUsedGb",     round2(memUsedGb));
        result.put("cpuLabel",      cpuTotalCores > 0
                ? round2(cpuUsedCores) + " / " + round2(cpuTotalCores) + " cores"
                : cpuPct + "%");
        result.put("memLabel",      memTotalGb > 0
                ? round2(memUsedGb) + " / " + round2(memTotalGb) + " GB"
                : memPct + "%");
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double parsePercent(String pct) {
        if (pct == null || pct.isBlank()) return 0;
        try { return Double.parseDouble(pct.replace("%", "").trim()); } catch (Exception e) { return 0; }
    }

    /** Parses K8s CPU quantity → decimal cores (e.g., "2000m" → 2.0, "4" → 4.0, "1234567890n" → 1.23) */
    private double parseMilliCores(String cpu) {
        if (cpu.endsWith("n")) {
            // nanocores — metrics-server returns CPU usage in this format
            try { return Double.parseDouble(cpu.substring(0, cpu.length() - 1)) / 1_000_000_000.0; } catch (Exception e) { return 0; }
        }
        if (cpu.endsWith("u")) {
            // microcores
            try { return Double.parseDouble(cpu.substring(0, cpu.length() - 1)) / 1_000_000.0; } catch (Exception e) { return 0; }
        }
        if (cpu.endsWith("m")) {
            // millicores
            try { return Double.parseDouble(cpu.substring(0, cpu.length() - 1)) / 1000.0; } catch (Exception e) { return 0; }
        }
        try { return Double.parseDouble(cpu); } catch (Exception e) { return 0; }
    }

    /** Parses K8s memory quantity → GB (e.g., "8110Mi" → 7.98, "4Gi" → 4.0) */
    private double parseMiToGb(String mem) {
        try {
            if (mem.endsWith("Ki")) return Double.parseDouble(mem.replace("Ki", "")) / 1024.0 / 1024.0;
            if (mem.endsWith("Mi")) return Double.parseDouble(mem.replace("Mi", "")) / 1024.0;
            if (mem.endsWith("Gi")) return Double.parseDouble(mem.replace("Gi", ""));
            if (mem.endsWith("Ti")) return Double.parseDouble(mem.replace("Ti", "")) * 1024.0;
            if (mem.endsWith("K"))  return Double.parseDouble(mem.replace("K",  "")) / 1024.0 / 1024.0;
            if (mem.endsWith("M"))  return Double.parseDouble(mem.replace("M",  "")) / 1024.0;
            if (mem.endsWith("G"))  return Double.parseDouble(mem.replace("G",  ""));
            return Double.parseDouble(mem) / 1024.0 / 1024.0 / 1024.0; // bytes
        } catch (Exception e) { return 0; }
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
