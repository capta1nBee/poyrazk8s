package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.IngressClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngressClassRepository extends JpaRepository<IngressClass, Long> {

    List<IngressClass> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<IngressClass> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<IngressClass> findByUid(String uid);
}
