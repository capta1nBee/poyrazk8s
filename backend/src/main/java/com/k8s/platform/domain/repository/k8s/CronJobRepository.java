package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.CronJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CronJobRepository extends JpaRepository<CronJob, Long> {

    List<CronJob> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<CronJob> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<CronJob> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace,
            String name);

    Optional<CronJob> findByUid(String uid);

    List<CronJob> findByClusterId(Long clusterId);

    List<CronJob> findByClusterIdAndNamespace(Long clusterId, String namespace);
}
