package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.K8sNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface K8sNodeRepository extends JpaRepository<K8sNode, Long> {

    List<K8sNode> findByClusterIdAndIsDeletedFalse(Long clusterId);

    long countByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<K8sNode> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<K8sNode> findByUid(String uid);

    List<K8sNode> findByClusterId(Long clusterId);
}
