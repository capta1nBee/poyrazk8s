package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.PriorityLevelConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriorityLevelConfigurationRepository extends JpaRepository<PriorityLevelConfiguration, Long> {

    List<PriorityLevelConfiguration> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<PriorityLevelConfiguration> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<PriorityLevelConfiguration> findByUid(String uid);
}
