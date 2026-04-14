package com.k8s.platform.service.security;

import com.k8s.platform.domain.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodic ClusterEye scan across every registered cluster.
 *
 * Interval is configurable via:
 *   cluster.eye.scan-interval-ms  (default: 300 000 ms = 5 minutes)
 *
 * The startup scan runs once after the application is ready, then the
 * scheduled interval takes over.  fixedDelay guarantees no overlap.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClusterEyeScanScheduler {

    private final ClusterRepository clusterRepository;
    private final ClusterEyeScanService scanService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${cluster.eye.scan-interval-ms:300000}")
    public void scheduledScan() {
        runAllClusters("scheduled");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("[ClusterEye] Application ready — running initial scan");
        runAllClusters("startup");
    }

    private void runAllClusters(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.debug("[ClusterEye] Skipping {} — previous scan still running", trigger);
            return;
        }
        try {
            var clusters = clusterRepository.findAll();
            log.info("[ClusterEye] [{}] Scanning {} cluster(s)", trigger, clusters.size());
            for (var cluster : clusters) {
                try {
                    int n = scanService.scanCluster(cluster.getUid());
                    log.info("[ClusterEye] Cluster '{}' — {} workloads scanned", cluster.getName(), n);
                } catch (Exception e) {
                    log.warn("[ClusterEye] Cluster '{}' scan failed: {}", cluster.getName(), e.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }
}
