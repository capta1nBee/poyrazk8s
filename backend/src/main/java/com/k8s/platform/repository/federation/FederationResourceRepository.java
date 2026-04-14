package com.k8s.platform.repository.federation;

import com.k8s.platform.domain.entity.federation.FederationResource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FederationResourceRepository extends JpaRepository<FederationResource, Long> {
    
    @EntityGraph(attributePaths = {"federation", "federation.masterCluster"})
    List<FederationResource> findByFederationId(Long federationId);
    
    @EntityGraph(attributePaths = {"federation", "federation.masterCluster"})
    List<FederationResource> findByNamespace(String namespace);

    @EntityGraph(attributePaths = {"federation", "federation.masterCluster"})
    List<FederationResource> findAll();

    @EntityGraph(attributePaths = {"federation", "federation.masterCluster"})
    Optional<FederationResource> findById(Long id);
}
