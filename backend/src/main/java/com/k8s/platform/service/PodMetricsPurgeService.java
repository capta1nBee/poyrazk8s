package com.k8s.platform.service;

import com.k8s.platform.domain.repository.PodMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Periodically purges pod_metrics older than the configured retention period.
 *
 * Config:
 *   pod-metrics.retention-days  (default: 7)
 *   pod-metrics.purge-interval-ms (default: 60000 = 1 min)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PodMetricsPurgeService {

    private final PodMetricRepository repository;

    @Value("${pod-metrics.retention-days:7}")
    private int retentionDays;

    @Scheduled(fixedDelayString = "${pod-metrics.purge-interval-ms:60000}")
    @Transactional
    public void purgeOldMetrics() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = repository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("[PodMetrics] Purged {} metrics older than {} days", deleted, retentionDays);
        }
    }
}
