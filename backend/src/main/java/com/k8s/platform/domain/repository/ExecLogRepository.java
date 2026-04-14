package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.ExecLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecLogRepository extends JpaRepository<ExecLog, Long> {
}
