package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.ValidatingAdmissionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidatingAdmissionPolicyRepository extends JpaRepository<ValidatingAdmissionPolicy, Long> {

    List<ValidatingAdmissionPolicy> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<ValidatingAdmissionPolicy> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<ValidatingAdmissionPolicy> findByUid(String uid);
}
