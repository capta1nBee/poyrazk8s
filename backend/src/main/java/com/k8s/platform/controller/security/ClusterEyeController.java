package com.k8s.platform.controller.security;

import com.k8s.platform.domain.entity.security.ClusterEyeResult;
import com.k8s.platform.domain.repository.security.ClusterEyeResultRepository;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.security.ClusterEyeScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for ClusterEye security scan results.
 *
 * GET  /api/k8s/{clusterUid}/security/eye/results          → filtered list
 * GET  /api/k8s/{clusterUid}/security/eye/summary          → severity totals
 * POST /api/k8s/{clusterUid}/security/eye/scan             → trigger manual scan
 */
@RestController
@RequestMapping("/api/k8s/{clusterUid}/security/eye")
@RequiredArgsConstructor
public class ClusterEyeController {

    private final ClusterEyeResultRepository repository;
    private final ClusterEyeScanService scanService;
    private final ResourceAuthorizationHelper authHelper;

    // ── List results with optional filters ────────────────────────────────────

    @GetMapping("/results")
    public List<Map<String, Object>> getResults(
            @PathVariable String clusterUid,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "ALL") String minSeverity) {

        // T1: namespace-scoped permission check — use '*' when no namespace filter is given
        String effectiveNs = (namespace != null && !namespace.isBlank()) ? namespace : "*";
        authHelper.checkPermissionOrThrow(clusterUid, effectiveNs, "ClusterEyeResult", "*", "view");

        return repository.findFiltered(clusterUid, namespace, kind, minSeverity)
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    // ── Single workload detail ────────────────────────────────────────────────

    @GetMapping("/results/{id}")
    public Map<String, Object> getResult(@PathVariable String clusterUid,
                                         @PathVariable Long id) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "ClusterEyeResult", "*", "view");
        ClusterEyeResult r = repository.findById(id)
                .filter(row -> row.getClusterUid().equals(clusterUid))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
        return toMap(r);
    }

    // ── Cluster-level severity summary ────────────────────────────────────────

    @GetMapping("/summary")
    public Map<String, Object> getSummary(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "ClusterEyeResult", "*", "view");

        List<Object[]> rows = repository.summaryByCluster(clusterUid);
        Object[] row = (rows != null && !rows.isEmpty()) ? rows.get(0) : new Object[]{0L, 0L, 0L, 0L};
        long critical  = row[0] != null ? ((Number) row[0]).longValue() : 0;
        long high      = row[1] != null ? ((Number) row[1]).longValue() : 0;
        long medium    = row[2] != null ? ((Number) row[2]).longValue() : 0;
        long workloads = row[3] != null ? ((Number) row[3]).longValue() : 0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("critical",  critical);
        summary.put("high",      high);
        summary.put("medium",    medium);
        summary.put("total",     critical + high + medium);
        summary.put("workloads", workloads);

        // Timestamp of most recent scan
        repository.findByClusterUidOrderByLastScannedAtDesc(clusterUid)
                .stream().findFirst()
                .ifPresent(r -> summary.put("lastScannedAt", r.getLastScannedAt().toString()));

        return summary;
    }

    // ── Manual scan trigger ───────────────────────────────────────────────────

    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> triggerScan(@PathVariable String clusterUid) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "ClusterEyeResult", "*", "scan");

        long start = System.currentTimeMillis();
        int scanned = scanService.scanCluster(clusterUid);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", scanned);
        result.put("elapsedMs", elapsed);
        result.put("message", "Scan complete — " + scanned + " workloads processed in " + elapsed + " ms");
        return ResponseEntity.ok(result);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(ClusterEyeResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             r.getId());
        m.put("clusterUid",     r.getClusterUid());
        m.put("namespace",      r.getNamespace());
        m.put("workloadKind",   r.getWorkloadKind());
        m.put("workloadName",   r.getWorkloadName());
        m.put("findings",       r.getFindings());        // raw JSON string
        m.put("criticalCount",  r.getCriticalCount());
        m.put("highCount",      r.getHighCount());
        m.put("mediumCount",    r.getMediumCount());
        m.put("totalCount",     r.getTotalCount());
        m.put("lastScannedAt",  r.getLastScannedAt() != null ? r.getLastScannedAt().toString() : null);
        return m;
    }
}
