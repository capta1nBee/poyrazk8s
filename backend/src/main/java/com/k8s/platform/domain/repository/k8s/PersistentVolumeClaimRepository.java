package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.PersistentVolumeClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersistentVolumeClaimRepository extends JpaRepository<PersistentVolumeClaim, Long> {

    List<PersistentVolumeClaim> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<PersistentVolumeClaim> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<PersistentVolumeClaim> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace, String name);

    Optional<PersistentVolumeClaim> findByUid(String uid);

    List<PersistentVolumeClaim> findByClusterIdAndPhaseAndIsDeletedFalse(Long clusterId, String phase);

    List<PersistentVolumeClaim> findByClusterId(Long clusterId);

    List<PersistentVolumeClaim> findByClusterIdAndNamespace(Long clusterId, String namespace);
}

