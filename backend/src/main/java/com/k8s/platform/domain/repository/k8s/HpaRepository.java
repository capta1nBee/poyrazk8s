package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.HorizontalPodAutoscaler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HpaRepository extends JpaRepository<HorizontalPodAutoscaler, Long> {

    List<HorizontalPodAutoscaler> findByClusterIdAndIsDeletedFalse(Long clusterId);

    long countByClusterIdAndIsDeletedFalse(Long clusterId);

    List<HorizontalPodAutoscaler> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<HorizontalPodAutoscaler> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(
            Long clusterId, String namespace, String name);

    Optional<HorizontalPodAutoscaler> findByUid(String uid);

    List<HorizontalPodAutoscaler> findByClusterId(Long clusterId);

    List<HorizontalPodAutoscaler> findByClusterIdAndNamespace(Long clusterId, String namespace);
}
