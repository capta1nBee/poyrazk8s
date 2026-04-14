package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.ClusterRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClusterRoleRepository extends JpaRepository<ClusterRole, Long> {

    List<ClusterRole> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<ClusterRole> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<ClusterRole> findByUid(String uid);
}
