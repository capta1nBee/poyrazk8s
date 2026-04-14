package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.SecurityRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityRuleRepository extends JpaRepository<SecurityRule, Long> {

    /**
     * Find rules by cluster UID and active status
     */
    List<SecurityRule> findByClusterUidAndIsActiveTrue(String clusterUid);

    /**
     * Find enabled rules by cluster UID
     */
    List<SecurityRule> findByClusterUidAndEnabledTrue(String clusterUid);

    /**
     * Find rules by cluster UID with pagination
     */
    Page<SecurityRule> findByClusterUidAndIsActiveTrue(String clusterUid, Pageable pageable);

    /**
     * Find rule by cluster UID and name
     */
    Optional<SecurityRule> findByClusterUidAndNameAndIsActiveTrue(String clusterUid, String name);

    /**
     * Find rules by priority
     */
    List<SecurityRule> findByClusterUidAndPriorityAndIsActiveTrue(String clusterUid, String priority);

    /**
     * Find rules by rule type
     */
    List<SecurityRule> findByClusterUidAndRuleTypeAndIsActiveTrue(String clusterUid, String ruleType);

    /**
     * Count active rules by cluster
     */
    long countByClusterUidAndIsActiveTrue(String clusterUid);

    /**
     * Count enabled rules by cluster
     */
    long countByClusterUidAndEnabledTrueAndIsActiveTrue(String clusterUid);

    /**
     * Search rules with custom query
     */
    @Query("SELECT r FROM SecurityRule r WHERE r.clusterUid = :clusterUid AND r.isActive = true " +
           "AND (LOWER(r.name) LIKE LOWER(concat('%', :searchTerm, '%')) " +
           "OR LOWER(r.description) LIKE LOWER(concat('%', :searchTerm, '%')) " +
           "OR LOWER(r.tagsJson) LIKE LOWER(concat('%', :searchTerm, '%')))")
    Page<SecurityRule> searchRules(@Param("clusterUid") String clusterUid,
                                   @Param("searchTerm") String searchTerm,
                                   Pageable pageable);
}
