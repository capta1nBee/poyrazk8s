package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.UserPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPolicyRepository extends JpaRepository<UserPolicy, Long> {

    List<UserPolicy> findByUserIdAndIsActiveTrue(Long userId);

    List<UserPolicy> findBySubjectNameAndIsActiveTrue(String subjectName);

    Optional<UserPolicy> findByIdAndIsActiveTrue(Long id);

    List<UserPolicy> findByIsActiveTrue();

    void deleteByUserId(Long userId);
}
