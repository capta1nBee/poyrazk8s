package com.k8s.platform.domain.repository.network;

import com.k8s.platform.domain.entity.network.NetworkTopologyEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkTopologyEdgeRepository extends JpaRepository<NetworkTopologyEdge, Long> {

       List<NetworkTopologyEdge> findByClusterUid(String clusterUid);

       List<NetworkTopologyEdge> findByClusterUidAndSourceNamespace(String clusterUid, String sourceNamespace);

       List<NetworkTopologyEdge> findByClusterUidAndTargetNamespace(String clusterUid, String targetNamespace);

       @Query("SELECT nte FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid " +
                     "AND (nte.sourceNamespace = :namespace OR nte.targetNamespace = :namespace)")
       List<NetworkTopologyEdge> findByClusterUidAndNamespace(
                     @Param("clusterUid") String clusterUid,
                     @Param("namespace") String namespace);

       @Query("SELECT nte FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid " +
                     "AND (nte.sourceNamespace IN :namespaces OR nte.targetNamespace IN :namespaces)")
       List<NetworkTopologyEdge> findByClusterUidAndNamespaces(
                     @Param("clusterUid") String clusterUid,
                     @Param("namespaces") List<String> namespaces);

       List<NetworkTopologyEdge> findByClusterUidAndSourceTypeAndSourceNameAndSourceNamespaceAndTargetTypeAndTargetNameAndTargetNamespaceAndProtocolAndPort(
                     String clusterUid,
                     String sourceType,
                     String sourceName,
                     String sourceNamespace,
                     String targetType,
                     String targetName,
                     String targetNamespace,
                     String protocol,
                     Integer port);

       @Query("SELECT DISTINCT nte.sourceNamespace FROM NetworkTopologyEdge nte " +
                     "WHERE nte.clusterUid = :clusterUid AND nte.sourceNamespace IS NOT NULL " +
                     "UNION " +
                     "SELECT DISTINCT nte.targetNamespace FROM NetworkTopologyEdge nte " +
                     "WHERE nte.clusterUid = :clusterUid AND nte.targetNamespace IS NOT NULL")
       List<String> findDistinctNamespaces(@Param("clusterUid") String clusterUid);

       // Get nodes for topology
       @Query("SELECT DISTINCT nte.sourceType, nte.sourceName, nte.sourceNamespace " +
                     "FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid")
       List<Object[]> findDistinctSourceNodes(@Param("clusterUid") String clusterUid);

       @Query("SELECT DISTINCT nte.targetType, nte.targetName, nte.targetNamespace " +
                     "FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid")
       List<Object[]> findDistinctTargetNodes(@Param("clusterUid") String clusterUid);

       // Update edge statistics
       @Modifying
       @Query("UPDATE NetworkTopologyEdge nte SET " +
                     "nte.flowCount = nte.flowCount + :flowCount, " +
                     "nte.totalBytes = nte.totalBytes + :bytes, " +
                     "nte.lastSeen = :lastSeen, " +
                     "nte.updatedAt = :updatedAt " +
                     "WHERE nte.id = :id")
       void updateEdgeStats(
                     @Param("id") Long id,
                     @Param("flowCount") Long flowCount,
                     @Param("bytes") Long bytes,
                     @Param("lastSeen") LocalDateTime lastSeen,
                     @Param("updatedAt") LocalDateTime updatedAt);

       // Cleanup old edges
       void deleteByClusterUidAndLastSeenBefore(String clusterUid, LocalDateTime before);

       // Get top edges by flow count
       @Query("SELECT nte FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid " +
                     "ORDER BY nte.flowCount DESC")
       List<NetworkTopologyEdge> findTopEdgesByFlowCount(@Param("clusterUid") String clusterUid);

       // Get top edges by bytes
       @Query("SELECT nte FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid " +
                     "ORDER BY nte.totalBytes DESC")
       List<NetworkTopologyEdge> findTopEdgesByBytes(@Param("clusterUid") String clusterUid);

       // Policy generation queries
       @Query("SELECT nte FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid " +
                     "AND (:namespace IS NULL OR nte.sourceNamespace = :namespace OR nte.targetNamespace = :namespace)")
       List<NetworkTopologyEdge> findByClusterUidAndNamespaceForPolicyGeneration(
                     @Param("clusterUid") String clusterUid,
                     @Param("namespace") String namespace);

       // Optimized queries split by direction for better index usage
       @Query("SELECT nte FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid " +
                     "AND nte.targetNamespace = :namespace")
       List<NetworkTopologyEdge> findByClusterUidAndTargetNamespaceForIngress(
                     @Param("clusterUid") String clusterUid,
                     @Param("namespace") String namespace);

       @Query("SELECT nte FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid " +
                     "AND nte.sourceNamespace = :namespace")
       List<NetworkTopologyEdge> findByClusterUidAndSourceNamespaceForEgress(
                     @Param("clusterUid") String clusterUid,
                     @Param("namespace") String namespace);

       @Query("SELECT nte FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid " +
                     "AND nte.targetNamespace = :namespace " +
                     "AND (nte.targetName IN :names OR nte.backendPodName IN :names)")
       List<NetworkTopologyEdge> findIngressEdgesByPodNames(
                     @Param("clusterUid") String clusterUid,
                     @Param("namespace") String namespace,
                     @Param("names") List<String> names);

       @Query("SELECT nte FROM NetworkTopologyEdge nte WHERE nte.clusterUid = :clusterUid " +
                     "AND nte.sourceNamespace = :namespace " +
                     "AND nte.sourceName IN :names")
       List<NetworkTopologyEdge> findEgressEdgesByPodNames(
                     @Param("clusterUid") String clusterUid,
                     @Param("namespace") String namespace,
                     @Param("names") List<String> names);
}
