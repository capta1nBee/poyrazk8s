package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value =
            "SELECT * FROM audit_logs a WHERE " +
            "(CAST(:username AS text) IS NULL OR lower(a.username) LIKE lower('%' || CAST(:username AS text) || '%')) AND " +
            "(CAST(:action AS text) IS NULL OR lower(a.action) LIKE lower('%' || CAST(:action AS text) || '%')) AND " +
            "(CAST(:details AS text) IS NULL OR lower(a.details) LIKE lower('%' || CAST(:details AS text) || '%')) AND " +
            "(CAST(:clusterUid AS text) IS NULL OR a.cluster_uid = CAST(:clusterUid AS text)) AND " +
            "(:filterByAllowed = false OR a.cluster_uid IN (:allowedClusterUids) OR a.cluster_uid IS NULL) " +
            "ORDER BY a.\"timestamp\" DESC",
            countQuery =
            "SELECT count(*) FROM audit_logs a WHERE " +
            "(CAST(:username AS text) IS NULL OR lower(a.username) LIKE lower('%' || CAST(:username AS text) || '%')) AND " +
            "(CAST(:action AS text) IS NULL OR lower(a.action) LIKE lower('%' || CAST(:action AS text) || '%')) AND " +
            "(CAST(:details AS text) IS NULL OR lower(a.details) LIKE lower('%' || CAST(:details AS text) || '%')) AND " +
            "(CAST(:clusterUid AS text) IS NULL OR a.cluster_uid = CAST(:clusterUid AS text)) AND " +
            "(:filterByAllowed = false OR a.cluster_uid IN (:allowedClusterUids) OR a.cluster_uid IS NULL)",
            nativeQuery = true)
    Page<AuditLog> findAllWithFilters(@Param("username") String username,
            @Param("action") String action,
            @Param("details") String details,
            @Param("clusterUid") String clusterUid,
            @Param("allowedClusterUids") java.util.List<String> allowedClusterUids,
            @Param("filterByAllowed") boolean filterByAllowed,
            Pageable pageable);

    /** Top N users by activity count in a cluster since a given timestamp (for reports). */
    @Query("SELECT a.username, COUNT(a) FROM AuditLog a " +
           "WHERE a.clusterUid = :clusterUid AND a.timestamp >= :since " +
           "GROUP BY a.username ORDER BY COUNT(a) DESC")
    java.util.List<Object[]> findTopUsersByCluster(
            @org.springframework.data.repository.query.Param("clusterUid") String clusterUid,
            @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    /** Top N actions by frequency in a cluster since a given timestamp (for reports). */
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a " +
           "WHERE a.clusterUid = :clusterUid AND a.timestamp >= :since " +
           "GROUP BY a.action ORDER BY COUNT(a) DESC")
    java.util.List<Object[]> findTopActionsByCluster(
            @org.springframework.data.repository.query.Param("clusterUid") String clusterUid,
            @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    /** Total audit event count for a cluster. */
    long countByClusterUid(String clusterUid);

    /** Recent audit events for a cluster. */
    java.util.List<AuditLog> findTop20ByClusterUidOrderByTimestampDesc(String clusterUid);
}
