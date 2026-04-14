package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findByClusterIdAndIsDeletedFalse(Long clusterId);

    long countByClusterIdAndIsDeletedFalse(Long clusterId);

    List<Service> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<Service> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<Service> findByUid(String uid);

    List<Service> findByClusterIdAndServiceType(Long clusterId, String serviceType);

    List<Service> findByClusterId(Long clusterId);

    List<Service> findByClusterIdAndNamespace(Long clusterId, String namespace);
}
