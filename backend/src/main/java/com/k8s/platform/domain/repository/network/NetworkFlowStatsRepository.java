package com.k8s.platform.domain.repository.network;

import com.k8s.platform.domain.entity.network.NetworkFlowStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NetworkFlowStatsRepository extends JpaRepository<NetworkFlowStats, Long> {

    List<NetworkFlowStats> findByClusterUidAndGranularity(String clusterUid, String granularity);

    List<NetworkFlowStats> findByClusterUidAndGranularityAndPeriodStartBetween(
            String clusterUid, String granularity, LocalDateTime start, LocalDateTime end);

    @Query("SELECT nfs FROM NetworkFlowStats nfs WHERE nfs.clusterUid = :clusterUid " +
           "AND nfs.granularity = :granularity " +
           "AND nfs.periodStart >= :start AND nfs.periodEnd <= :end " +
           "ORDER BY nfs.periodStart")
    List<NetworkFlowStats> findStatsByTimeRange(
            @Param("clusterUid") String clusterUid,
            @Param("granularity") String granularity,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT nfs FROM NetworkFlowStats nfs WHERE nfs.clusterUid = :clusterUid " +
           "AND nfs.granularity = :granularity " +
           "AND nfs.flowType = :flowType " +
           "AND nfs.periodStart >= :start AND nfs.periodEnd <= :end " +
           "ORDER BY nfs.periodStart")
    List<NetworkFlowStats> findStatsByFlowTypeAndTimeRange(
            @Param("clusterUid") String clusterUid,
            @Param("granularity") String granularity,
            @Param("flowType") String flowType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Cleanup old stats
    void deleteByClusterUidAndPeriodEndBefore(String clusterUid, LocalDateTime before);
}
