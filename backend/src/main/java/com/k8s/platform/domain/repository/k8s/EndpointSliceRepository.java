package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.EndpointSlice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EndpointSliceRepository extends JpaRepository<EndpointSlice, Long> {
    List<EndpointSlice> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<EndpointSlice> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<EndpointSlice> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<EndpointSlice> findByUid(String uid);
}
