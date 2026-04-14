package com.k8s.platform.domain.repository.network;

import com.k8s.platform.domain.entity.network.GeneratedNetworkPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GeneratedNetworkPolicyRepository extends JpaRepository<GeneratedNetworkPolicy, Long> {

    List<GeneratedNetworkPolicy> findByClusterUid(String clusterUid);

    List<GeneratedNetworkPolicy> findByClusterUidAndNamespace(String clusterUid, String namespace);

    List<GeneratedNetworkPolicy> findByClusterUidAndNamespaceAndPolicyType(
            String clusterUid, String namespace, String policyType);

    List<GeneratedNetworkPolicy> findByClusterUidAndStatus(String clusterUid, String status);

    List<GeneratedNetworkPolicy> findByClusterUidAndNamespaceAndStatus(
            String clusterUid, String namespace, String status);

    Optional<GeneratedNetworkPolicy> findByClusterUidAndNamespaceAndName(
            String clusterUid, String namespace, String name);

    @Query("SELECT gnp FROM GeneratedNetworkPolicy gnp WHERE gnp.clusterUid = :clusterUid " +
           "AND gnp.namespace = :namespace AND gnp.policyType = :policyType AND gnp.status != 'deleted'")
    List<GeneratedNetworkPolicy> findActiveByClusterAndNamespaceAndType(
            @Param("clusterUid") String clusterUid,
            @Param("namespace") String namespace,
            @Param("policyType") String policyType);

    @Query("SELECT gnp FROM GeneratedNetworkPolicy gnp WHERE gnp.clusterUid = :clusterUid " +
           "AND gnp.namespace = :namespace AND gnp.status = 'applied'")
    List<GeneratedNetworkPolicy> findAppliedByClusterAndNamespace(
            @Param("clusterUid") String clusterUid,
            @Param("namespace") String namespace);

    @Query("SELECT COUNT(gnp) FROM GeneratedNetworkPolicy gnp WHERE gnp.clusterUid = :clusterUid " +
           "AND gnp.status = :status")
    long countByClusterUidAndStatus(
            @Param("clusterUid") String clusterUid,
            @Param("status") String status);

    boolean existsByClusterUidAndNamespaceAndName(String clusterUid, String namespace, String name);
}
