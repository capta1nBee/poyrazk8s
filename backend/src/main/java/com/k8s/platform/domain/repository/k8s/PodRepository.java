package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.Pod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PodRepository extends JpaRepository<Pod, Long> {

    List<Pod> findByClusterIdAndIsDeletedFalse(Long clusterId);

    long countByClusterIdAndIsDeletedFalse(Long clusterId);

    List<Pod> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<Pod> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace, String name);

    Optional<Pod> findByUid(String uid);

    List<Pod> findByClusterIdAndPhaseAndIsDeletedFalse(Long clusterId, String phase);

    List<Pod> findByClusterIdAndNodeNameAndIsDeletedFalse(Long clusterId, String nodeName);

    List<Pod> findByClusterId(Long clusterId);

    List<Pod> findByClusterIdAndNamespace(Long clusterId, String namespace);

    List<Pod> findByClusterIdAndNamespaceAndLabelsContainingAndIsDeletedFalse(Long clusterId, String namespace,
            String labelPattern);
}
