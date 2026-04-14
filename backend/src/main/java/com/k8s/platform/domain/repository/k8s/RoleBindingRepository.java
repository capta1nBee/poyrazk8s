package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.RoleBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleBindingRepository extends JpaRepository<RoleBinding, Long> {

    List<RoleBinding> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<RoleBinding> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<RoleBinding> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<RoleBinding> findByUid(String uid);
}
