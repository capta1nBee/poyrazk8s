package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.PodMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PodMetricRepository extends JpaRepository<PodMetric, Long> {

    // ── Time-series history for a specific pod ──────────────────────────────

    @Query("""
        SELECT m FROM PodMetric m
        WHERE m.clusterUid = :uid
          AND m.namespace  = :ns
          AND m.podName    = :pod
          AND m.collectedAt >= :since
        ORDER BY m.collectedAt ASC
    """)
    List<PodMetric> findHistory(
            @Param("uid")   String clusterUid,
            @Param("ns")    String namespace,
            @Param("pod")   String podName,
            @Param("since") LocalDateTime since);

    // ── Namespace-level aggregated history (sum of all pods in namespace) ────

    @Query("""
        SELECT m.collectedAt,
               SUM(m.cpuMillicores),
               SUM(m.memoryBytes)
        FROM PodMetric m
        WHERE m.clusterUid = :uid
          AND m.namespace  = :ns
          AND m.collectedAt >= :since
        GROUP BY m.collectedAt
        ORDER BY m.collectedAt ASC
    """)
    List<Object[]> findNamespaceHistory(
            @Param("uid")   String clusterUid,
            @Param("ns")    String namespace,
            @Param("since") LocalDateTime since);

    // ── Top pods by CPU (latest snapshot) ───────────────────────────────────

    @Query("""
        SELECT m.namespace, m.podName, m.cpuMillicores, m.memoryBytes
        FROM PodMetric m
        WHERE m.clusterUid = :uid
          AND (:ns IS NULL OR m.namespace = :ns)
          AND m.collectedAt = (
              SELECT MAX(m2.collectedAt)
              FROM PodMetric m2
              WHERE m2.clusterUid = :uid
          )
        ORDER BY m.cpuMillicores DESC
    """)
    List<Object[]> findTopPodsByCpu(
            @Param("uid") String clusterUid,
            @Param("ns")  String namespace);

    // ── Top pods by Memory (latest snapshot) ────────────────────────────────

    @Query("""
        SELECT m.namespace, m.podName, m.cpuMillicores, m.memoryBytes
        FROM PodMetric m
        WHERE m.clusterUid = :uid
          AND (:ns IS NULL OR m.namespace = :ns)
          AND m.collectedAt = (
              SELECT MAX(m2.collectedAt)
              FROM PodMetric m2
              WHERE m2.clusterUid = :uid
          )
        ORDER BY m.memoryBytes DESC
    """)
    List<Object[]> findTopPodsByMemory(
            @Param("uid") String clusterUid,
            @Param("ns")  String namespace);

    // ── Distinct namespaces with metrics data ───────────────────────────────

    @Query("""
        SELECT DISTINCT m.namespace
        FROM PodMetric m
        WHERE m.clusterUid = :uid
        ORDER BY m.namespace
    """)
    List<String> findDistinctNamespaces(@Param("uid") String clusterUid);

    // ── Distinct pods in a namespace ────────────────────────────────────────

    @Query("""
        SELECT DISTINCT m.podName
        FROM PodMetric m
        WHERE m.clusterUid = :uid
          AND m.namespace  = :ns
        ORDER BY m.podName
    """)
    List<String> findDistinctPods(
            @Param("uid") String clusterUid,
            @Param("ns")  String namespace);

    // ── Namespace summary (latest snapshot aggregated) ──────────────────────

    @Query("""
        SELECT m.namespace,
               COUNT(DISTINCT m.podName),
               SUM(m.cpuMillicores),
               SUM(m.memoryBytes)
        FROM PodMetric m
        WHERE m.clusterUid = :uid
          AND m.collectedAt = (
              SELECT MAX(m2.collectedAt)
              FROM PodMetric m2
              WHERE m2.clusterUid = :uid
          )
        GROUP BY m.namespace
        ORDER BY SUM(m.cpuMillicores) DESC
    """)
    List<Object[]> findNamespaceSummary(@Param("uid") String clusterUid);

    // ── Retention cleanup ───────────────────────────────────────────────────

    @Modifying
    @Query("DELETE FROM PodMetric m WHERE m.collectedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
