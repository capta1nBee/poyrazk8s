package com.k8s.platform.repository.federation;

import com.k8s.platform.domain.entity.federation.Federation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FederationRepository extends JpaRepository<Federation, Long> {
    Optional<Federation> findByName(String name);
}
