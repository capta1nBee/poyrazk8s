package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.PodMetric;
import com.k8s.platform.domain.repository.PodMetricRepository;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.k8s.K8sClientService;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for pod metrics — T1 namespace-scoped permission checks.
 */
@Slf4j
@RestController
@RequestMapping("/api/k8s/{clusterUid}/pod-metrics")
@RequiredArgsConstructor
public class PodMetricsController {

    private final PodMetricRepository repository;
    private final ResourceAuthorizationHelper authHelper;
    private final K8sClientService k8sClientService;

    // ── Namespaces with metrics data (T1 filtered) ────────────────────────

    @GetMapping("/namespaces")
    public List<String> getNamespaces(@PathVariable String clusterUid) {
        // Return only namespaces the user has PodMetric view permission for
        return repository.findDistinctNamespaces(clusterUid).stream()
                .filter(ns -> authHelper.hasPermission(clusterUid, ns, "PodMetric", "*", "view"))
                .collect(Collectors.toList());
    }

    // ── Pods in a namespace ─────────────────────────────────────────────────

    @GetMapping("/pods")
    public List<String> getPods(@PathVariable String clusterUid,
                                @RequestParam String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "PodMetric", "*", "view");
        return repository.findDistinctPods(clusterUid, namespace);
    }

    // ── Time-series history for a single pod ────────────────────────────────

    @GetMapping("/history")
    public Map<String, Object> getHistory(
            @PathVariable String clusterUid,
            @RequestParam String namespace,
            @RequestParam String podName,
            @RequestParam(defaultValue = "1h") String range) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "PodMetric", podName, "view");

        LocalDateTime since = parseSince(range);
        List<PodMetric> metrics = repository.findHistory(clusterUid, namespace, podName, since);

        List<Map<String, Object>> series = metrics.stream().map(m -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", m.getCollectedAt().toString());
            p.put("cpuMillicores", m.getCpuMillicores());
            p.put("memoryBytes", m.getMemoryBytes());
            p.put("memoryMi", Math.round(m.getMemoryBytes() / (1024.0 * 1024.0) * 100.0) / 100.0);
            return p;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("namespace", namespace);
        result.put("podName", podName);
        result.put("range", range);
        result.put("dataPoints", series.size());
        result.put("series", series);
        return result;
    }

    // ── Namespace-level aggregated history ───────────────────────────────────

    @GetMapping("/namespace-history")
    public Map<String, Object> getNamespaceHistory(
            @PathVariable String clusterUid,
            @RequestParam String namespace,
            @RequestParam(defaultValue = "1h") String range) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "PodMetric", "*", "view");

        LocalDateTime since = parseSince(range);
        List<Object[]> rows = repository.findNamespaceHistory(clusterUid, namespace, since);

        List<Map<String, Object>> series = rows.stream().map(r -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", r[0].toString());
            p.put("cpuMillicores", ((Number) r[1]).intValue());
            p.put("memoryBytes", ((Number) r[2]).longValue());
            p.put("memoryMi", Math.round(((Number) r[2]).longValue() / (1024.0 * 1024.0) * 100.0) / 100.0);
            return p;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("namespace", namespace);
        result.put("range", range);
        result.put("dataPoints", series.size());
        result.put("series", series);
        return result;
    }

    // ── Top pods (current snapshot) ─────────────────────────────────────────

    @GetMapping("/top")
    public Map<String, Object> getTopPods(
            @PathVariable String clusterUid,
            @RequestParam(required = false) String namespace,
            @RequestParam(defaultValue = "cpu") String sortBy,
            @RequestParam(defaultValue = "20") int limit) {

        String effectiveNs = (namespace != null && !namespace.isBlank()) ? namespace : "*";
        authHelper.checkPermissionOrThrow(clusterUid, effectiveNs, "PodMetric", "*", "view");

        String nsParam = (namespace != null && !namespace.isBlank()) ? namespace : null;
        List<Object[]> rows = "memory".equals(sortBy)
                ? repository.findTopPodsByMemory(clusterUid, nsParam)
                : repository.findTopPodsByCpu(clusterUid, nsParam);

        // T1 filter: when no specific namespace is selected, filter by permission
        List<Map<String, Object>> pods = rows.stream()
                .filter(r -> authHelper.hasPermission(clusterUid, (String) r[0], "PodMetric", "*", "view"))
                .limit(limit).map(r -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("namespace", r[0]);
            p.put("podName", r[1]);
            p.put("cpuMillicores", ((Number) r[2]).intValue());
            p.put("memoryBytes", ((Number) r[3]).longValue());
            p.put("memoryMi", Math.round(((Number) r[3]).longValue() / (1024.0 * 1024.0) * 100.0) / 100.0);
            return p;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sortBy", sortBy);
        result.put("count", pods.size());
        result.put("pods", pods);
        return result;
    }


    // ── Namespace summary (T1 filtered — only authorized namespaces) ──────

    @GetMapping("/summary")
    public List<Map<String, Object>> getSummary(@PathVariable String clusterUid) {
        return repository.findNamespaceSummary(clusterUid).stream()
                // T1 filter: only show namespaces user has permission for
                .filter(r -> authHelper.hasPermission(clusterUid, (String) r[0], "PodMetric", "*", "view"))
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("namespace", r[0]);
                    m.put("podCount", ((Number) r[1]).intValue());
                    m.put("totalCpuMillicores", ((Number) r[2]).intValue());
                    m.put("totalMemoryBytes", ((Number) r[3]).longValue());
                    m.put("totalMemoryMi", Math.round(((Number) r[3]).longValue() / (1024.0 * 1024.0) * 100.0) / 100.0);
                    return m;
                }).collect(Collectors.toList());
    }

    // ── Metrics API health check ───────────────────────────────────────────

    @GetMapping("/status")
    public Map<String, Object> getMetricsApiStatus(@PathVariable String clusterUid) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            KubernetesClient client = k8sClientService.getClient(clusterUid);
            var metricsList = client.top().pods().metrics();
            boolean available = metricsList != null && metricsList.getItems() != null;
            result.put("available", available);
            result.put("message", available ? "Metrics API is available" : "Metrics API returned empty response");
        } catch (Exception e) {
            log.warn("[PodMetrics] Metrics API not available for cluster {}: {}", clusterUid, e.getMessage());
            result.put("available", false);
            result.put("message", "Metrics API not available: " + e.getMessage());
        }
        return result;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private LocalDateTime parseSince(String range) {
        return switch (range) {
            case "15m" -> LocalDateTime.now().minusMinutes(15);
            case "30m" -> LocalDateTime.now().minusMinutes(30);
            case "1h"  -> LocalDateTime.now().minusHours(1);
            case "6h"  -> LocalDateTime.now().minusHours(6);
            case "24h" -> LocalDateTime.now().minusHours(24);
            case "7d"  -> LocalDateTime.now().minusDays(7);
            default    -> LocalDateTime.now().minusHours(1);
        };
    }
}