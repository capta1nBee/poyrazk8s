package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.MonitoringConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MonitoringConfigRepository extends JpaRepository<MonitoringConfig, Long> {

    /**
     * Find monitoring config by cluster UID
     */
    Optional<MonitoringConfig> findByClusterUid(String clusterUid);

    /**
     * Check if config exists for cluster
     */
    boolean existsByClusterUid(String clusterUid);
}
