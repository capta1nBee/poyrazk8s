package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.VolumeAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VolumeAttachmentRepository extends JpaRepository<VolumeAttachment, Long> {

    List<VolumeAttachment> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<VolumeAttachment> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<VolumeAttachment> findByUid(String uid);
}
