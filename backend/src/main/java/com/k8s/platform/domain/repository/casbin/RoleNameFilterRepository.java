package com.k8s.platform.domain.repository.casbin;

import com.k8s.platform.domain.entity.casbin.RoleNameFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleNameFilterRepository extends JpaRepository<RoleNameFilter, Long> {

    List<RoleNameFilter> findByRoleName(String roleName);

    void deleteByRoleName(String roleName);

    /**
     * Find all name filters for a set of role names, optionally narrowed by clusterUid
     * and resourceKind. Wildcard '*' in cluster_uid means all clusters.
     */
    @Query("SELECT f FROM RoleNameFilter f WHERE f.roleName IN :roleNames " +
           "AND (f.clusterUid = '*' OR f.clusterUid = :clusterUid) " +
           "AND f.resourceKind = :resourceKind")
    List<RoleNameFilter> findEffectiveFilters(
            @Param("roleNames") List<String> roleNames,
            @Param("clusterUid") String clusterUid,
            @Param("resourceKind") String resourceKind
    );
}
