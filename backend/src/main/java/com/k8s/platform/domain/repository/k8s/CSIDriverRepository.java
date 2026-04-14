package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.CSIDriver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CSIDriverRepository extends JpaRepository<CSIDriver, Long> {

    List<CSIDriver> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<CSIDriver> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<CSIDriver> findByUid(String uid);
}
