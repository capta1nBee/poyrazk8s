package com.k8s.platform.domain.repository.authorization;

import com.k8s.platform.domain.entity.authorization.UIPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UIPermissionRepository extends JpaRepository<UIPermission, Long> {

    Optional<UIPermission> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}

