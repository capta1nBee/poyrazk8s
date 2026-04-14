package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByClusterIdAndIsDeletedFalse(Long clusterId);

    List<Job> findByClusterIdAndNamespaceAndIsDeletedFalse(Long clusterId, String namespace);

    Optional<Job> findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(Long clusterId, String namespace, String name);

    Optional<Job> findByUid(String uid);

    List<Job> findByOwnerRefsContaining(String cronJobUid);

    List<Job> findByClusterId(Long clusterId);

    List<Job> findByClusterIdAndNamespace(Long clusterId, String namespace);
}
