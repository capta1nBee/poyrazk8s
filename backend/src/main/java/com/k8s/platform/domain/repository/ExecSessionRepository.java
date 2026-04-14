package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.ExecSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExecSessionRepository extends JpaRepository<ExecSession, Long> {
    Optional<ExecSession> findBySessionId(String sessionId);
    long countByClusterId(String clusterId);
}
