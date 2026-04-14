package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.AllowedCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AllowedCommandRepository extends JpaRepository<AllowedCommand, Long> {
    /** Query by Casbin RoleTemplate names (new system). */
    List<AllowedCommand> findByRoleTemplateNameIn(Collection<String> roleTemplateNames);
}
