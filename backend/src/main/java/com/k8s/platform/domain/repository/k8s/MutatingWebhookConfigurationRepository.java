package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.MutatingWebhookConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MutatingWebhookConfigurationRepository extends JpaRepository<MutatingWebhookConfiguration, Long> {

    List<MutatingWebhookConfiguration> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<MutatingWebhookConfiguration> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<MutatingWebhookConfiguration> findByUid(String uid);
}
