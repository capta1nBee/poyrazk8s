package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.Ingress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngressRepository extends JpaRepository<Ingress, Long> {

    List<Ingress> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<Ingress> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<Ingress> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<Ingress> findByUid(String uid);

    List<Ingress> findByClusterId(Long clusterId);

    List<Ingress> findByClusterIdAndNamespace(Long clusterId, String namespace);
}
