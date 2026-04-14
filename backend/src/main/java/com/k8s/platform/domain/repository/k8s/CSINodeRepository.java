package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.CSINode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CSINodeRepository extends JpaRepository<CSINode, Long> {

    List<CSINode> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<CSINode> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<CSINode> findByUid(String uid);
}
