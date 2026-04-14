package com.k8s.platform.domain.repository.casbin;

import com.k8s.platform.domain.entity.casbin.CasbinRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CasbinRuleRepository extends JpaRepository<CasbinRule, Long> {

    List<CasbinRule> findByPtype(String ptype);

    @Query("SELECT r FROM CasbinRule r WHERE r.ptype = :ptype AND r.v0 = :v0 AND r.v1 = :v1")
    List<CasbinRule> findByPtypeAndV0AndV1(@Param("ptype") String ptype,
                                            @Param("v0") String v0,
                                            @Param("v1") String v1);

    @Query("SELECT r FROM CasbinRule r WHERE r.ptype = 'g' AND r.v0 = :username")
    List<CasbinRule> findRoleBindingsByUsername(@Param("username") String username);

    @Query("SELECT r FROM CasbinRule r WHERE r.ptype = 'g' AND r.v1 = :roleName")
    List<CasbinRule> findRoleBindingsByRoleName(@Param("roleName") String roleName);

    @Query("SELECT r FROM CasbinRule r WHERE r.ptype = 'p' AND r.v0 = :roleName")
    List<CasbinRule> findPoliciesByRole(@Param("roleName") String roleName);

    @Modifying
    @Transactional
    @Query("DELETE FROM CasbinRule r WHERE r.ptype = :ptype AND r.v0 = :v0 AND r.v1 = :v1 AND r.v2 = :v2")
    void deleteByPtypeAndV0V1V2(@Param("ptype") String ptype,
                                 @Param("v0") String v0,
                                 @Param("v1") String v1,
                                 @Param("v2") String v2);

    @Modifying
    @Transactional
    @Query("DELETE FROM CasbinRule r WHERE r.ptype = 'p' AND r.v0 = :roleName")
    void deleteAllPoliciesForRole(@Param("roleName") String roleName);

    @Modifying
    @Transactional
    @Query("DELETE FROM CasbinRule r WHERE r.ptype = 'g' AND r.v0 = :username AND r.v1 = :roleName AND r.v2 = :clusterUid")
    void deleteRoleBinding(@Param("username") String username,
                           @Param("roleName") String roleName,
                           @Param("clusterUid") String clusterUid);

    boolean existsByPtypeAndV0AndV1AndV2AndV3AndV4(
            String ptype, String v0, String v1, String v2, String v3, String v4);
}
