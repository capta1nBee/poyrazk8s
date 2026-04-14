package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    List<Deployment> findByClusterIdAndIsDeletedFalse(Long clusterId);

    long countByClusterIdAndIsDeletedFalse(Long clusterId);

    List<Deployment> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<Deployment> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<Deployment> findByUid(String uid);

    List<Deployment> findByClusterId(Long clusterId);

    List<Deployment> findByClusterIdAndNamespace(Long clusterId, String namespace);
}
