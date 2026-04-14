package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.ValidatingAdmissionPolicyBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidatingAdmissionPolicyBindingRepository
        extends JpaRepository<ValidatingAdmissionPolicyBinding, Long> {

    List<ValidatingAdmissionPolicyBinding> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<ValidatingAdmissionPolicyBinding> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<ValidatingAdmissionPolicyBinding> findByUid(String uid);
}
