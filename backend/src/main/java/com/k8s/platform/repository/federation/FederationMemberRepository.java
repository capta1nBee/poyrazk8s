package com.k8s.platform.repository.federation;

import com.k8s.platform.domain.entity.federation.FederationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FederationMemberRepository extends JpaRepository<FederationMember, Long> {
    List<FederationMember> findByFederationId(Long federationId);
}
