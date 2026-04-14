package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.ReplicationController;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReplicationControllerRepository extends JpaRepository<ReplicationController, Long> {

    List<ReplicationController> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<ReplicationController> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<ReplicationController> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId,
            String namespace, String name);

    Optional<ReplicationController> findByUid(String uid);
}
