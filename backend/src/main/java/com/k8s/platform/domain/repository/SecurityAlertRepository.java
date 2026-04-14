package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.SecurityAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {

    /**
     * Find pending (unacknowledged and unresolved) alerts by cluster
     */
    List<SecurityAlert> findByClusterUidAndIsAcknowledgedFalseAndResolvedFalse(String clusterUid);

    /**
     * Find alerts by cluster with pagination
     */
    Page<SecurityAlert> findByClusterUid(String clusterUid, Pageable pageable);

    /**
     * Find recent alerts by cluster
     */
    List<SecurityAlert> findByClusterUidOrderByCreatedAtDesc(String clusterUid);

    /**
     * Find alerts by cluster and priority
     */
    Page<SecurityAlert> findByClusterUidAndPriority(String clusterUid, String priority, Pageable pageable);

    /**
     * Find alerts by cluster and pod name
     */
    Page<SecurityAlert> findByClusterUidAndPodName(String clusterUid, String podName, Pageable pageable);

    /**
     * Find alerts by cluster and namespace
     */
    Page<SecurityAlert> findByClusterUidAndNamespaceName(String clusterUid, String namespace, Pageable pageable);

    /**
     * Find alert by fingerprint for deduplication
     */
    Optional<SecurityAlert> findByFingerprint(String fingerprint);

    /**
     * Count pending alerts by cluster
     */
    long countByClusterUidAndIsAcknowledgedFalseAndResolvedFalse(String clusterUid);

    /**
     * Count alerts by severity
     */
    long countByClusterUidAndPriority(String clusterUid, String priority);

    /**
     * Find alerts created after timestamp by cluster
     */
    List<SecurityAlert> findByClusterUidAndCreatedAtAfter(String clusterUid, LocalDateTime dateTime);

    /**
     * Find alerts by rule name
     */
    List<SecurityAlert> findByClusterUidAndRuleName(String clusterUid, String ruleName);

    /**
     * Advanced search with multiple filters
     */
    @Query("SELECT a FROM SecurityAlert a WHERE a.clusterUid = :clusterUid " +
           "AND (:priority IS NULL OR a.priority = :priority) " +
           "AND (:namespace IS NULL OR a.namespaceName = :namespace) " +
           "AND (:podName IS NULL OR a.podName = :podName) " +
           "AND (:acknowledged IS NULL OR a.isAcknowledged = :acknowledged) " +
           "AND (:resolved IS NULL OR a.resolved = :resolved) " +
           "AND a.createdAt >= :startDate " +
           "ORDER BY a.createdAt DESC")
    Page<SecurityAlert> searchAlerts(@Param("clusterUid") String clusterUid,
                                     @Param("priority") String priority,
                                     @Param("namespace") String namespace,
                                     @Param("podName") String podName,
                                     @Param("acknowledged") Boolean acknowledged,
                                     @Param("resolved") Boolean resolved,
                                     @Param("startDate") LocalDateTime startDate,
                                     Pageable pageable);
}
