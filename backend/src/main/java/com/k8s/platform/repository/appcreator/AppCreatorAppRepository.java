package com.k8s.platform.repository.appcreator;

import com.k8s.platform.domain.entity.appcreator.AppCreatorApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppCreatorAppRepository extends JpaRepository<AppCreatorApp, UUID> {
    List<AppCreatorApp> findAllByClusterUidOrderByCreatedAtDesc(String clusterUid);
    List<AppCreatorApp> findAllByClusterUidAndStatusOrderByCreatedAtDesc(String clusterUid, String status);
    Optional<AppCreatorApp> findByIdAndClusterUid(UUID id, String clusterUid);
    boolean existsByClusterUidAndNameAndNamespace(String clusterUid, String name, String namespace);
}

