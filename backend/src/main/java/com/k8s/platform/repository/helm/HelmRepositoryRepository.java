package com.k8s.platform.repository.helm;

import com.k8s.platform.domain.entity.helm.HelmRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HelmRepositoryRepository extends JpaRepository<HelmRepository, UUID> {
    List<HelmRepository> findAllByClusterUid(String clusterUid);
    Optional<HelmRepository> findByClusterUidAndName(String clusterUid, String name);
}
