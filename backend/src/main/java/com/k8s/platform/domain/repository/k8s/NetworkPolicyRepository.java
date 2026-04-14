package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.NetworkPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkPolicyRepository extends JpaRepository<NetworkPolicy, Long> {

    List<NetworkPolicy> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<NetworkPolicy> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<NetworkPolicy> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<NetworkPolicy> findByUid(String uid);
}
