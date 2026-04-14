package com.k8s.platform.repository.appcreator;

import com.k8s.platform.domain.entity.appcreator.AppCreatorDeployHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppCreatorDeployHistoryRepository extends JpaRepository<AppCreatorDeployHistory, UUID> {
    List<AppCreatorDeployHistory> findAllByAppIdOrderByCreatedAtDesc(UUID appId);
    List<AppCreatorDeployHistory> findAllByClusterUidOrderByCreatedAtDesc(String clusterUid);
}

