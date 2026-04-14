package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.CertificateSigningRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateSigningRequestRepository extends JpaRepository<CertificateSigningRequest, Long> {

    List<CertificateSigningRequest> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<CertificateSigningRequest> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<CertificateSigningRequest> findByUid(String uid);
}
