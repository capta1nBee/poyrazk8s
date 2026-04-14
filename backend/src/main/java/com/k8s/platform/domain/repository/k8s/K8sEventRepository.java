package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.K8sEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface K8sEventRepository extends JpaRepository<K8sEvent, Long> {
    List<K8sEvent> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<K8sEvent> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    List<K8sEvent> findByClusterIdAndNamespaceAndInvolvedObjectKindAndInvolvedObjectNameAndIsDeletedFalse(
            Long clusterId, String namespace, String involvedObjectKind, String involvedObjectName);

    Optional<K8sEvent> findByUid(String uid);
}
