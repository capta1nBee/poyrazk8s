package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.Lease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaseRepository extends JpaRepository<Lease, Long> {
    List<Lease> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<Lease> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<Lease> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace, String name);

    Optional<Lease> findByUid(String uid);
}
