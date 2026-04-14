package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.ReplicaSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReplicaSetRepository extends JpaRepository<ReplicaSet, Long> {

    List<ReplicaSet> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<ReplicaSet> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<ReplicaSet> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<ReplicaSet> findByUid(String uid);

    List<ReplicaSet> findByOwnerRefsContaining(String deploymentUid);

    List<ReplicaSet> findByClusterId(Long clusterId);

    List<ReplicaSet> findByClusterIdAndNamespace(Long clusterId, String namespace);
}
