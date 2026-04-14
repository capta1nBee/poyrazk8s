package com.k8s.platform.service;

import com.k8s.platform.domain.entity.PodMetric;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.PodMetricRepository;
import com.k8s.platform.service.k8s.K8sClientService;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically collects pod CPU/memory metrics from the Kubernetes metrics-server
 * (equivalent of 'kubectl top pods --all-namespaces') and persists them for
 * historical charting.
 *
 * Interval: pod-metrics.collect-interval-ms (default 30s)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PodMetricsCollector {

    private final ClusterRepository clusterRepository;
    private final PodMetricRepository podMetricRepository;
    private final K8sClientService k8sClientService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${pod-metrics.collect-interval-ms:30000}")
    public void scheduledCollect() {
        collectAll("scheduled");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        collectAll("startup");
    }

    private void collectAll(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.debug("[PodMetrics] Skipping {} — previous collection still running", trigger);
            return;
        }
        try {
            var clusters = clusterRepository.findAll();
            for (var cluster : clusters) {
                try {
                    int n = collectCluster(cluster.getUid());
                    log.debug("[PodMetrics] Cluster '{}' — {} pod metrics collected", cluster.getName(), n);
                } catch (Exception e) {
                    log.warn("[PodMetrics] Cluster '{}' collection failed: {}", cluster.getName(), e.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }

    @Transactional
    public int collectCluster(String clusterUid) {
        KubernetesClient client = k8sClientService.getClient(clusterUid);
        PodMetricsList metricsList = client.top().pods().metrics();
        if (metricsList == null || metricsList.getItems() == null) return 0;

        LocalDateTime now = LocalDateTime.now();
        List<PodMetric> batch = new ArrayList<>();

        for (PodMetrics pm : metricsList.getItems()) {
            String ns = pm.getMetadata().getNamespace();
            String podName = pm.getMetadata().getName();

            // Sum all containers in the pod
            int totalCpu = 0;
            long totalMem = 0;
            if (pm.getContainers() != null) {
                for (ContainerMetrics cm : pm.getContainers()) {
                    if (cm.getUsage() == null) continue;
                    var cpuQ = cm.getUsage().get("cpu");
                    var memQ = cm.getUsage().get("memory");
                    if (cpuQ != null) totalCpu += parseCpuMillicores(cpuQ.toString());
                    if (memQ != null) totalMem += parseMemoryBytes(memQ.toString());
                }
            }

            batch.add(PodMetric.builder()
                    .clusterUid(clusterUid)
                    .namespace(ns)
                    .podName(podName)
                    .cpuMillicores(totalCpu)
                    .memoryBytes(totalMem)
                    .collectedAt(now)
                    .build());

            // Batch flush every 200 rows
            if (batch.size() >= 200) {
                podMetricRepository.saveAll(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            podMetricRepository.saveAll(batch);
        }

        return metricsList.getItems().size();
    }

    // ── Parsers ─────────────────────────────────────────────────────────────

    /** Parse K8s CPU quantity → millicores (e.g. "250m"→250, "1"→1000, "500n"→0) */
    static int parseCpuMillicores(String cpu) {
        try {
            if (cpu.endsWith("n"))  return (int) (Long.parseLong(cpu.substring(0, cpu.length()-1)) / 1_000_000);
            if (cpu.endsWith("u"))  return (int) (Long.parseLong(cpu.substring(0, cpu.length()-1)) / 1_000);
            if (cpu.endsWith("m"))  return Integer.parseInt(cpu.substring(0, cpu.length()-1));
            return (int) (Double.parseDouble(cpu) * 1000);
        } catch (Exception e) { return 0; }
    }

    /** Parse K8s memory quantity → bytes (e.g. "128Mi"→134217728, "1Gi"→1073741824) */
    static long parseMemoryBytes(String mem) {
        try {
            if (mem.endsWith("Ki")) return Long.parseLong(mem.substring(0, mem.length()-2)) * 1024;
            if (mem.endsWith("Mi")) return Long.parseLong(mem.substring(0, mem.length()-2)) * 1024 * 1024;
            if (mem.endsWith("Gi")) return Long.parseLong(mem.substring(0, mem.length()-2)) * 1024 * 1024 * 1024;
            if (mem.endsWith("Ti")) return Long.parseLong(mem.substring(0, mem.length()-2)) * 1024L * 1024 * 1024 * 1024;
            if (mem.endsWith("K") || mem.endsWith("k")) return Long.parseLong(mem.substring(0, mem.length()-1)) * 1000;
            if (mem.endsWith("M"))  return Long.parseLong(mem.substring(0, mem.length()-1)) * 1000_000;
            if (mem.endsWith("G"))  return Long.parseLong(mem.substring(0, mem.length()-1)) * 1000_000_000;
            return Long.parseLong(mem);
        } catch (Exception e) { return 0; }
    }
}
