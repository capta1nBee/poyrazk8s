package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.StatefulSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StatefulSetRepository extends JpaRepository<StatefulSet, Long> {

    List<StatefulSet> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<StatefulSet> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<StatefulSet> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<StatefulSet> findByUid(String uid);

    List<StatefulSet> findByClusterId(Long clusterId);

    List<StatefulSet> findByClusterIdAndNamespace(Long clusterId, String namespace);
}
