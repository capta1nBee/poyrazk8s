package com.k8s.platform.service.network;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Purges old network-flow data using a <em>table-rotation</em> strategy
 * instead of row-by-row DELETE — O(1) regardless of table size.
 *
 * <h3>Strategy (H2 partition-drop simulation)</h3>
 * <pre>
 *   network_flows_1  ←── active (inserts go here)
 *   network_flows_2  ←── inactive (empty / ready for next window)
 *
 *   When retentionMinutes elapses since last rotation:
 *     1. Switch active pointer  (network_flows_1 → network_flows_2)
 *     2. TRUNCATE old table     (network_flows_1)  ← O(1), no DELETE
 * </pre>
 *
 * <p>Both tables are always visible through the {@code network_flows} UNION-ALL
 * view, so all JPA read queries continue to work without change.
 *
 * <p>Configuration properties (all optional):
 * <pre>
 *   network.flow.retention-minutes   = 15     # window per rotation table
 *   network.flow.purge-interval-ms   = 30000  # how often to check
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkFlowPurgeService {

    private final JdbcTemplate jdbcTemplate;
    private final NetworkFlowTableRouter tableRouter;

    /** How many minutes each rotation table window covers. */
    @Value("${network.flow.retention-minutes:15}")
    private int retentionMinutes;

    /** Guards against overlapping executions. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ── Scheduled check ───────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${network.flow.purge-interval-ms:30000}")
    public void scheduledCheck() {
        runRotationCheck("scheduled");
    }

    // ── Startup check ─────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[NetworkFlowPurge] Application ready — running initial rotation check");
        runRotationCheck("startup");
    }

    // ── Core rotation logic ───────────────────────────────────────────────────

    private void runRotationCheck(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.debug("[NetworkFlowPurge] Skipping {} — previous check still running", trigger);
            return;
        }
        try {
            LocalDateTime lastRotation = queryLastRotation();
            if (lastRotation == null) {
                log.warn("[NetworkFlowPurge] Could not read last_rotation — skipping check");
                return;
            }

            LocalDateTime rotationDue = lastRotation.plusMinutes(retentionMinutes);
            if (LocalDateTime.now().isBefore(rotationDue)) {
                log.debug("[NetworkFlowPurge] [{}] Next rotation due at {} — nothing to do",
                        trigger, rotationDue);
                return;
            }

            log.info("[NetworkFlowPurge] [{}] Retention window elapsed (last={}, retentionMin={}) — rotating tables",
                    trigger, lastRotation, retentionMinutes);

            long t = System.currentTimeMillis();
            tableRouter.rotate();
            log.info("[NetworkFlowPurge] Rotation complete in {} ms — active table is now '{}'",
                    System.currentTimeMillis() - t, tableRouter.getActiveTable());

        } catch (Exception ex) {
            log.error("[NetworkFlowPurge] Rotation check failed: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }

    private LocalDateTime queryLastRotation() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT last_rotation FROM network_flow_rotation_state WHERE id = 1",
                    LocalDateTime.class);
        } catch (Exception e) {
            log.warn("[NetworkFlowPurge] Could not query last_rotation: {}", e.getMessage());
            return null;
        }
    }
}