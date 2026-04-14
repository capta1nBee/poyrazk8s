package com.k8s.platform.repository.appcreator;

import com.k8s.platform.domain.entity.appcreator.RegistryConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistryConnectionRepository extends JpaRepository<RegistryConnection, UUID> {

    /** List ALL connections for a cluster — visible to any authorized user */
    List<RegistryConnection> findAllByClusterUidOrderByCreatedAtDesc(String clusterUid);

    List<RegistryConnection> findAllByClusterUidAndUserIdOrderByCreatedAtDesc(String clusterUid, Long userId);

    Optional<RegistryConnection> findByIdAndClusterUid(UUID id, String clusterUid);

    Optional<RegistryConnection> findByIdAndClusterUidAndUserId(UUID id, String clusterUid, Long userId);

    boolean existsByClusterUidAndUserIdAndName(String clusterUid, Long userId, String name);
}

