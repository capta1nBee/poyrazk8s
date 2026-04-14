package com.k8s.platform.domain.repository.network;

import com.k8s.platform.domain.entity.network.NetworkFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NetworkFlowRepository extends JpaRepository<NetworkFlow, Long>, JpaSpecificationExecutor<NetworkFlow> {

    // Basic queries
    Page<NetworkFlow> findByClusterUid(String clusterUid, Pageable pageable);
    
    Page<NetworkFlow> findByClusterUidAndFlowType(String clusterUid, String flowType, Pageable pageable);
    
    Page<NetworkFlow> findByClusterUidAndTimestampBetween(
            String clusterUid, LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    // Source based queries
    Page<NetworkFlow> findByClusterUidAndSourceNamespace(
            String clusterUid, String sourceNamespace, Pageable pageable);
    
    Page<NetworkFlow> findByClusterUidAndSourcePodName(
            String clusterUid, String sourcePodName, Pageable pageable);
    
    Page<NetworkFlow> findByClusterUidAndSourcePodNameContaining(
            String clusterUid, String sourcePodNamePattern, Pageable pageable);
    
    // Destination based queries
    Page<NetworkFlow> findByClusterUidAndDestinationNamespace(
            String clusterUid, String destinationNamespace, Pageable pageable);
    
    Page<NetworkFlow> findByClusterUidAndDestinationPodName(
            String clusterUid, String destinationPodName, Pageable pageable);
    
    // Protocol based queries
    Page<NetworkFlow> findByClusterUidAndProtocol(
            String clusterUid, String protocol, Pageable pageable);
    
    // Complex queries
    @Query("SELECT nf FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid " +
           "AND (:flowType IS NULL OR nf.flowType = :flowType) " +
           "AND (:sourceNamespace IS NULL OR nf.sourceNamespace = :sourceNamespace) " +
           "AND (:destinationNamespace IS NULL OR nf.destinationNamespace = :destinationNamespace) " +
           "AND (:sourcePodName IS NULL OR nf.sourcePodName LIKE %:sourcePodName%) " +
           "AND (:destinationPodName IS NULL OR nf.destinationPodName LIKE %:destinationPodName%) " +
           "AND (:protocol IS NULL OR nf.protocol = :protocol) " +
           "AND (:startTime IS NULL OR nf.timestamp >= :startTime) " +
           "AND (:endTime IS NULL OR nf.timestamp <= :endTime)")
    Page<NetworkFlow> findByFilters(
            @Param("clusterUid") String clusterUid,
            @Param("flowType") String flowType,
            @Param("sourceNamespace") String sourceNamespace,
            @Param("destinationNamespace") String destinationNamespace,
            @Param("sourcePodName") String sourcePodName,
            @Param("destinationPodName") String destinationPodName,
            @Param("protocol") String protocol,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    // Statistics queries
    @Query("SELECT nf.flowType as flowType, COUNT(nf) as count, SUM(nf.bytes) as totalBytes " +
           "FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid " +
           "AND nf.timestamp >= :startTime AND nf.timestamp <= :endTime " +
           "GROUP BY nf.flowType")
    List<Object[]> getFlowStatsByType(
            @Param("clusterUid") String clusterUid,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT nf.sourceNamespace as namespace, COUNT(nf) as count, SUM(nf.bytes) as totalBytes " +
           "FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid " +
           "AND nf.timestamp >= :startTime AND nf.timestamp <= :endTime " +
           "GROUP BY nf.sourceNamespace")
    List<Object[]> getFlowStatsBySourceNamespace(
            @Param("clusterUid") String clusterUid,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT nf.destinationNamespace as namespace, COUNT(nf) as count, SUM(nf.bytes) as totalBytes " +
           "FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid " +
           "AND nf.timestamp >= :startTime AND nf.timestamp <= :endTime " +
           "GROUP BY nf.destinationNamespace")
    List<Object[]> getFlowStatsByDestinationNamespace(
            @Param("clusterUid") String clusterUid,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query("SELECT nf.protocol as protocol, COUNT(nf) as count, SUM(nf.bytes) as totalBytes " +
           "FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid " +
           "AND nf.timestamp >= :startTime AND nf.timestamp <= :endTime " +
           "GROUP BY nf.protocol")
    List<Object[]> getFlowStatsByProtocol(
            @Param("clusterUid") String clusterUid,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // Count queries
    long countByClusterUid(String clusterUid);
    
    long countByClusterUidAndFlowType(String clusterUid, String flowType);
    
    @Query("SELECT COUNT(nf) FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid " +
           "AND nf.timestamp >= :startTime AND nf.timestamp <= :endTime")
    long countByClusterUidAndTimestampBetween(
            @Param("clusterUid") String clusterUid,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // Get distinct values for filters
    @Query("SELECT DISTINCT nf.sourceNamespace FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid AND nf.sourceNamespace IS NOT NULL")
    List<String> findDistinctSourceNamespaces(@Param("clusterUid") String clusterUid);

    @Query("SELECT DISTINCT nf.destinationNamespace FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid AND nf.destinationNamespace IS NOT NULL")
    List<String> findDistinctDestinationNamespaces(@Param("clusterUid") String clusterUid);

    @Query("SELECT DISTINCT nf.flowType FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid")
    List<String> findDistinctFlowTypes(@Param("clusterUid") String clusterUid);

    @Query("SELECT DISTINCT nf.protocol FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid")
    List<String> findDistinctProtocols(@Param("clusterUid") String clusterUid);

    @Query("SELECT DISTINCT nf.sourcePodName FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid AND nf.sourcePodName IS NOT NULL")
    List<String> findDistinctSourcePods(@Param("clusterUid") String clusterUid);

    @Query("SELECT DISTINCT nf.destinationPodName FROM NetworkFlow nf WHERE nf.clusterUid = :clusterUid AND nf.destinationPodName IS NOT NULL")
    List<String> findDistinctDestinationPods(@Param("clusterUid") String clusterUid);

    // Cleanup old data
    void deleteByClusterUidAndTimestampBefore(String clusterUid, LocalDateTime before);
}
