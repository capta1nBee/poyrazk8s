package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.CustomResourceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomResourceDefinitionRepository extends JpaRepository<CustomResourceDefinition, Long> {

    List<CustomResourceDefinition> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<CustomResourceDefinition> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<CustomResourceDefinition> findByUid(String uid);
}
