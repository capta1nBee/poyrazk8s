package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.ValidatingWebhookConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidatingWebhookConfigurationRepository extends JpaRepository<ValidatingWebhookConfiguration, Long> {

    List<ValidatingWebhookConfiguration> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<ValidatingWebhookConfiguration> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<ValidatingWebhookConfiguration> findByUid(String uid);
}
