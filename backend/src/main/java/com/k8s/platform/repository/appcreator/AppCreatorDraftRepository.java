package com.k8s.platform.repository.appcreator;

import com.k8s.platform.domain.entity.appcreator.AppCreatorDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppCreatorDraftRepository extends JpaRepository<AppCreatorDraft, UUID> {
    List<AppCreatorDraft> findAllByClusterUidAndCreatedByOrderByUpdatedAtDesc(String clusterUid, Long createdBy);
    Optional<AppCreatorDraft> findByAppId(UUID appId);
    Optional<AppCreatorDraft> findByIdAndClusterUid(UUID id, String clusterUid);
}

