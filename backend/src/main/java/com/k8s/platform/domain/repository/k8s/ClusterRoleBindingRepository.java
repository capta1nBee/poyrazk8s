package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.ClusterRoleBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClusterRoleBindingRepository extends JpaRepository<ClusterRoleBinding, Long> {

    List<ClusterRoleBinding> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<ClusterRoleBinding> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<ClusterRoleBinding> findByUid(String uid);
}
