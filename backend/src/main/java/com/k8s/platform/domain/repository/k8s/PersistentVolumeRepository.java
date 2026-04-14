package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.PersistentVolume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersistentVolumeRepository extends JpaRepository<PersistentVolume, Long> {

    List<PersistentVolume> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<PersistentVolume> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<PersistentVolume> findByUid(String uid);

    List<PersistentVolume> findByClusterIdAndPhaseAndIsDeletedFalse(Long clusterId, String phase);

    List<PersistentVolume> findByClusterIdAndStorageClassNameAndIsDeletedFalse(Long clusterId, String storageClassName);
}

