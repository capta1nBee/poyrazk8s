package com.k8s.platform.domain.repository.network;

import com.k8s.platform.domain.entity.network.NetworkPolicyMigration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkPolicyMigrationRepository extends JpaRepository<NetworkPolicyMigration, Long> {

    List<NetworkPolicyMigration> findByPolicyIdOrderByVersionDesc(Long policyId);

    List<NetworkPolicyMigration> findByPolicyIdOrderByVersionAsc(Long policyId);

    Optional<NetworkPolicyMigration> findByPolicyIdAndVersion(Long policyId, Integer version);

    @Query("SELECT MAX(npm.version) FROM NetworkPolicyMigration npm WHERE npm.policyId = :policyId")
    Optional<Integer> findMaxVersionByPolicyId(@Param("policyId") Long policyId);

    @Query("SELECT npm FROM NetworkPolicyMigration npm WHERE npm.policyId = :policyId " +
           "AND npm.version = (SELECT MAX(n.version) FROM NetworkPolicyMigration n WHERE n.policyId = :policyId)")
    Optional<NetworkPolicyMigration> findLatestByPolicyId(@Param("policyId") Long policyId);

    @Query("SELECT npm FROM NetworkPolicyMigration npm WHERE npm.policyId = :policyId " +
           "AND npm.rollbackAt IS NULL ORDER BY npm.version DESC")
    List<NetworkPolicyMigration> findActiveByPolicyId(@Param("policyId") Long policyId);

    long countByPolicyId(Long policyId);

    void deleteByPolicyId(Long policyId);
}
