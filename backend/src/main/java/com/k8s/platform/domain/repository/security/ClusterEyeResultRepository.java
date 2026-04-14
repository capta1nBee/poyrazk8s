package com.k8s.platform.domain.repository.security;

import com.k8s.platform.domain.entity.security.ClusterEyeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClusterEyeResultRepository extends JpaRepository<ClusterEyeResult, Long> {

    List<ClusterEyeResult> findByClusterUidOrderByLastScannedAtDesc(String clusterUid);

    List<ClusterEyeResult> findByClusterUidAndNamespaceOrderByLastScannedAtDesc(
            String clusterUid, String namespace);

    List<ClusterEyeResult> findByClusterUidAndWorkloadKindOrderByLastScannedAtDesc(
            String clusterUid, String workloadKind);

    Optional<ClusterEyeResult> findByClusterUidAndNamespaceAndWorkloadKindAndWorkloadName(
            String clusterUid, String namespace, String workloadKind, String workloadName);

    void deleteByClusterUid(String clusterUid);

    /** Severity summary for a cluster */
    @Query("""
        SELECT
            COALESCE(SUM(r.criticalCount), 0),
            COALESCE(SUM(r.highCount), 0),
            COALESCE(SUM(r.mediumCount), 0),
            COUNT(r)
        FROM ClusterEyeResult r
        WHERE r.clusterUid = :uid
    """)
    List<Object[]> summaryByCluster(@Param("uid") String clusterUid);

    @Query("""
        SELECT r FROM ClusterEyeResult r
        WHERE r.clusterUid = :uid
          AND (:ns IS NULL OR r.namespace = :ns)
          AND (:kind IS NULL OR r.workloadKind = :kind)
          AND (:minSeverity = 'ALL'
               OR (:minSeverity = 'CRITICAL' AND r.criticalCount > 0)
               OR (:minSeverity = 'HIGH'     AND (r.criticalCount > 0 OR r.highCount > 0))
               OR (:minSeverity = 'MEDIUM'   AND r.totalCount > 0))
        ORDER BY r.criticalCount DESC, r.highCount DESC, r.lastScannedAt DESC
    """)
    List<ClusterEyeResult> findFiltered(
            @Param("uid")         String clusterUid,
            @Param("ns")          String namespace,
            @Param("kind")        String workloadKind,
            @Param("minSeverity") String minSeverity);
}
