package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {
    List<Action> findByPageId(Long pageId);
    
    // Include actions where is_active is TRUE or NULL (for backwards compatibility)
    @Query("SELECT a FROM Action a WHERE a.page.id = :pageId AND (a.isActive = true OR a.isActive IS NULL)")
    List<Action> findByPageIdAndIsActiveTrue(@Param("pageId") Long pageId);
    
    Optional<Action> findByPageIdAndActionCode(Long pageId, String actionCode);
    
    // Include actions where is_active is TRUE or NULL (for backwards compatibility)
    @Query("SELECT a FROM Action a WHERE a.resourceKind = :resourceKind AND (a.isActive = true OR a.isActive IS NULL)")
    List<Action> findByResourceKindAndIsActiveTrue(@Param("resourceKind") String resourceKind);
    
    // Include actions where is_active is TRUE or NULL (for backwards compatibility)
    @Query("SELECT a FROM Action a WHERE a.actionCode = :actionCode AND (a.isActive = true OR a.isActive IS NULL)")
    List<Action> findByActionCodeAndIsActiveTrue(@Param("actionCode") String actionCode);

    /** All distinct action codes across all active actions — used by RBAC UI */
    @Query("SELECT DISTINCT a.actionCode FROM Action a WHERE a.isActive = true OR a.isActive IS NULL")
    List<String> findDistinctActionCodes();
}
