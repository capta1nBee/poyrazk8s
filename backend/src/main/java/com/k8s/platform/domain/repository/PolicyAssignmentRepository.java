package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.PolicyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyAssignmentRepository extends JpaRepository<PolicyAssignment, Long> {

    List<PolicyAssignment> findByPolicyId(Long policyId);

    List<PolicyAssignment> findByClusterUidAndNamespace(String clusterUid, String namespace);

    List<PolicyAssignment> findByClusterUidAndResourceKind(String clusterUid, String resourceKind);

    void deleteByPolicyId(Long policyId);
}
