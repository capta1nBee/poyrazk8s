package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.DaemonSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DaemonSetRepository extends JpaRepository<DaemonSet, Long> {

    List<DaemonSet> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<DaemonSet> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<DaemonSet> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<DaemonSet> findByUid(String uid);

    List<DaemonSet> findByClusterId(Long clusterId);

    List<DaemonSet> findByClusterIdAndNamespace(Long clusterId, String namespace);
}
