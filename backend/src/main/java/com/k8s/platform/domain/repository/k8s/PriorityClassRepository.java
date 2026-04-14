package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.PriorityClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriorityClassRepository extends JpaRepository<PriorityClass, Long> {

    List<PriorityClass> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<PriorityClass> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<PriorityClass> findByUid(String uid);
}
