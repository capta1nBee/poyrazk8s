package com.k8s.platform.domain.repository.casbin;

import com.k8s.platform.domain.entity.casbin.RoleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleTemplateRepository extends JpaRepository<RoleTemplate, Long> {
    Optional<RoleTemplate> findByName(String name);
    List<RoleTemplate> findByIsActiveTrueOrderByCreatedAtDesc();
    boolean existsByName(String name);
}
