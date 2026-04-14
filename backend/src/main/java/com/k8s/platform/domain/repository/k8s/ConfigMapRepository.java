package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.ConfigMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigMapRepository extends JpaRepository<ConfigMap, Long> {

    List<ConfigMap> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<ConfigMap> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<ConfigMap> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<ConfigMap> findByUid(String uid);
}
