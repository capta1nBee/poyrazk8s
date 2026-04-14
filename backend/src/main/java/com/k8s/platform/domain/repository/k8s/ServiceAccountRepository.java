package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.ServiceAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceAccountRepository extends JpaRepository<ServiceAccount, Long> {

    List<ServiceAccount> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<ServiceAccount> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<ServiceAccount> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<ServiceAccount> findByUid(String uid);
}
