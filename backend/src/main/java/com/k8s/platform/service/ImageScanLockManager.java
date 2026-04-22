package com.k8s.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory lock manager to prevent duplicate vulnerability scans for the same image
 * within the same JVM instance.
 *
 * <p><b>LIMITATION:</b> This lock is <b>NOT distributed</b>. If the application runs with
 * multiple instances (e.g. behind a load balancer or in a Kubernetes Deployment with replicas &gt; 1),
 * each instance maintains its own independent lock set. This means the same image on the same cluster
 * could be scanned concurrently by different instances, causing duplicate work but no data corruption
 * (last-write-wins on the DB).
 *
 * <p>For multi-instance deployments, consider replacing this with a distributed lock mechanism such as:
 * <ul>
 *   <li>Database-based advisory lock (PostgreSQL pg_advisory_lock)</li>
 *   <li>Redis-based lock (Redisson / Spring Integration Redis Lock)</li>
 *   <li>Ensuring only a single instance runs scheduled scans (e.g. ShedLock)</li>
 * </ul>
 */
@Component
public class ImageScanLockManager {

    private static final Logger log = LoggerFactory.getLogger(ImageScanLockManager.class);

    /** Maximum time a lock can be held before it is considered stale and auto-released. */
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);

    /** Maps lock key → acquisition timestamp for TTL-based expiry. */
    private final Map<String, Instant> activeScans = new ConcurrentHashMap<>();

    private static String key(String clusterId, String image) {
        return clusterId + "|" + image;
    }

    public boolean acquire(String clusterId, String image) {
        String k = key(clusterId, image);
        Instant now = Instant.now();

        // Atomic compute: either insert new or replace stale — no race window
        boolean[] acquired = {false};
        activeScans.compute(k, (key, existing) -> {
            if (existing == null) {
                // No lock held — acquire
                acquired[0] = true;
                return now;
            }
            if (Duration.between(existing, now).compareTo(LOCK_TTL) > 0) {
                // Stale lock — force-replace atomically
                log.warn("Stale lock detected for '{}' (held since {}), force-releasing", key, existing);
                acquired[0] = true;
                return now;
            }
            // Lock is fresh — reject
            acquired[0] = false;
            return existing;
        });

        return acquired[0];
    }

    public void release(String clusterId, String image) {
        activeScans.remove(key(clusterId, image));
    }

    /** Returns the number of currently active locks (for monitoring/debugging). */
    public int activeCount() {
        return activeScans.size();
    }

    /** Periodically evicts stale locks that were never released (e.g. due to thread interruption). */
    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void evictStaleLocks() {
        Instant cutoff = Instant.now().minus(LOCK_TTL);
        int before = activeScans.size();

        activeScans.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                log.warn("Evicting stale scan lock: {}", entry.getKey());
                return true;
            }
            return false;
        });

        int evicted = before - activeScans.size();
        if (evicted > 0) {
            log.info("Evicted {} stale scan lock(s), {} remaining", evicted, activeScans.size());
        }
    }
}
