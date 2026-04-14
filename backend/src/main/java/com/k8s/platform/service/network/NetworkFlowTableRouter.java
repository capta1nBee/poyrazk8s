package com.k8s.platform.service.network;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks which of the two physical network-flow tables (network_flows_1 /
 * network_flows_2) is currently accepting inserts and coordinates rotation.
 *
 * <p>Rotation strategy (O(1) purge via partition-drop simulation):
 * <ol>
 *   <li>New flows are INSERTed into the <em>active</em> table via JdbcTemplate.</li>
 *   <li>After one retention window the purge service calls {@link #rotate()},
 *       which:
 *       <ul>
 *         <li>switches the active pointer to the other table,</li>
 *         <li>TRUNCATEs the old active table (instant, no per-row work).</li>
 *       </ul></li>
 *   <li>Both tables are always visible to JPA reads through the
 *       {@code network_flows} UNION-ALL view.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkFlowTableRouter {

    public static final String TABLE_1 = "network_flows_1";
    public static final String TABLE_2 = "network_flows_2";

    private final JdbcTemplate jdbcTemplate;

    /** Currently accepting inserts. */
    private final AtomicReference<String> activeTable = new AtomicReference<>(TABLE_1);

    @PostConstruct
    public void init() {
        try {
            String stored = jdbcTemplate.queryForObject(
                    "SELECT active_table FROM network_flow_rotation_state WHERE id = 1",
                    String.class);
            if (stored != null && (TABLE_1.equals(stored) || TABLE_2.equals(stored))) {
                activeTable.set(stored);
            }
            log.info("[NetworkFlowRouter] Active table: {}", activeTable.get());
        } catch (Exception e) {
            log.warn("[NetworkFlowRouter] Could not read rotation state, defaulting to {}: {}",
                    TABLE_1, e.getMessage());
        }
    }

    /** Returns the table name currently accepting inserts. */
    public String getActiveTable() {
        return activeTable.get();
    }

    /** Returns the table that is NOT currently accepting inserts. */
    public String getInactiveTable() {
        return TABLE_1.equals(activeTable.get()) ? TABLE_2 : TABLE_1;
    }

    /**
     * Atomically switches the active table and TRUNCATEs the old one.
     * Called by {@link NetworkFlowPurgeService} when the retention window expires.
     */
    public synchronized void rotate() {
        String oldActive = getActiveTable();
        String newActive = getInactiveTable();

        // 1. Switch the pointer
        activeTable.set(newActive);

        // 2. Persist new state
        try {
            jdbcTemplate.update(
                    "UPDATE network_flow_rotation_state SET active_table = ?, last_rotation = NOW() WHERE id = 1",
                    newActive);
        } catch (Exception e) {
            log.warn("[NetworkFlowRouter] Could not persist rotation state: {}", e.getMessage());
        }

        // 3. Truncate the old table — O(1) regardless of row count
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + oldActive);
            log.info("[NetworkFlowRouter] Rotated {} → {} (truncated {})", oldActive, newActive, oldActive);
        } catch (Exception e) {
            // Non-fatal: the old table still exists and inserts already go to the new one
            log.error("[NetworkFlowRouter] TRUNCATE {} failed: {}", oldActive, e.getMessage(), e);
        }
    }
}
