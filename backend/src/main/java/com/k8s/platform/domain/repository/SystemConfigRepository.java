package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKey(String configKey);

    List<SystemConfig> findByConfigCategory(String configCategory);

    List<SystemConfig> findByConfigKeyStartingWith(String prefix);

    List<SystemConfig> findByIsEncryptedTrue();
}
