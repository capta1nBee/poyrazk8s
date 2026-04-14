package com.k8s.platform.repository.appcreator;

import com.k8s.platform.domain.entity.appcreator.GitConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GitConnectionRepository extends JpaRepository<GitConnection, UUID> {

    /** List ALL connections for a cluster — visible to any authorized user */
    List<GitConnection> findAllByClusterUidOrderByCreatedAtDesc(String clusterUid);

    List<GitConnection> findAllByClusterUidAndUserIdOrderByCreatedAtDesc(String clusterUid, Long userId);

    Optional<GitConnection> findByIdAndClusterUid(UUID id, String clusterUid);

    Optional<GitConnection> findByIdAndClusterUidAndUserId(UUID id, String clusterUid, Long userId);

    boolean existsByClusterUidAndUserIdAndName(String clusterUid, Long userId, String name);
}

