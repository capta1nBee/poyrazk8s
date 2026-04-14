package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.ExecSessionRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecSessionRecordingRepository extends JpaRepository<ExecSessionRecording, Long> {
    List<ExecSessionRecording> findBySessionUidOrderByCreatedAtAsc(String sessionUid);
}
