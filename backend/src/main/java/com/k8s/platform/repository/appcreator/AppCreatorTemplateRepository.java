package com.k8s.platform.repository.appcreator;

import com.k8s.platform.domain.entity.appcreator.AppCreatorTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppCreatorTemplateRepository extends JpaRepository<AppCreatorTemplate, UUID> {

    @Query("SELECT t FROM AppCreatorTemplate t WHERE t.isPublic = true OR t.clusterUid = :clusterUid ORDER BY t.name")
    List<AppCreatorTemplate> findAvailableForCluster(@Param("clusterUid") String clusterUid);

    List<AppCreatorTemplate> findAllByClusterUidOrderByCreatedAtDesc(String clusterUid);

    List<AppCreatorTemplate> findAllByIsPublicTrueOrderByName();
}

