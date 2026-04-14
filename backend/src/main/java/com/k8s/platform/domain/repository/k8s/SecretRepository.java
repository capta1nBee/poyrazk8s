package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.Secret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecretRepository extends JpaRepository<Secret, Long> {

    List<Secret> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<Secret> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<Secret> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace, String name);

    Optional<Secret> findByUid(String uid);
}
