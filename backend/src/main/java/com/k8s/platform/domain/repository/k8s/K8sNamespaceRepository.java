package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.K8sNamespace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface K8sNamespaceRepository extends JpaRepository<K8sNamespace, Long> {
    List<K8sNamespace> findByClusterIdAndIsDeletedFalse(Long clusterId);

    long countByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<K8sNamespace> findByUid(String uid);

    Optional<K8sNamespace> findByClusterIdAndName(Long clusterId, String name);

    List<K8sNamespace> findByClusterId(Long clusterId);
}
