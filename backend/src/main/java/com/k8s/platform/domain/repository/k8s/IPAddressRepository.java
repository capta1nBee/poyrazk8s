package com.k8s.platform.domain.repository.k8s;

import com.k8s.platform.domain.entity.k8s.IPAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IPAddressRepository extends JpaRepository<IPAddress, Long> {

    List<IPAddress> findByClusterIdAndIsDeletedFalse(Long clusterId);

    Optional<IPAddress> findByClusterIdAndNameAndIsDeletedFalse(Long clusterId, String name);

    Optional<IPAddress> findByUid(String uid);
}
