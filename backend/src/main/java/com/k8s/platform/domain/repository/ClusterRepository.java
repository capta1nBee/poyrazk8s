package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClusterRepository extends JpaRepository<Cluster, Long> {

    Optional<Cluster> findByName(String name);

    Optional<Cluster> findByUid(String uid);

    List<Cluster> findByIsActiveTrue();

    Boolean existsByName(String name);

    Boolean existsByUid(String uid);
}
