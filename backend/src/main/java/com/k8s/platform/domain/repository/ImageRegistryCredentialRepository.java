package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.ImageRegistryCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImageRegistryCredentialRepository extends JpaRepository<ImageRegistryCredential, Long> {

    List<ImageRegistryCredential> findByClusterUidOrderByRegistryUrlAsc(String clusterUid);

    Optional<ImageRegistryCredential> findByClusterUidAndRegistryUrl(String clusterUid, String registryUrl);

    void deleteByClusterUidAndId(String clusterUid, Long id);
}
