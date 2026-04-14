package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.K8sRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface K8sRoleRepository extends JpaRepository<K8sRole, Long> {

    List<K8sRole> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<K8sRole> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<K8sRole> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<K8sRole> findByUid(String uid);
}
