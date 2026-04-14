package com.k8s.platform.domain.repository;

import com.k8s.platform.domain.entity.Backup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BackupRepository extends JpaRepository<Backup, Long> {
    
    List<Backup> findByClusterIdOrderByCreatedAtDesc(Long clusterId);
    
    List<Backup> findByClusterUidOrderByCreatedAtDesc(String clusterUid);
    
    List<Backup> findAllByOrderByCreatedAtDesc();
    
    List<Backup> findByStatusOrderByCreatedAtDesc(String status);
    
    Optional<Backup> findFirstByClusterIdOrderByCreatedAtDesc(Long clusterId);
    
    @Query("SELECT b FROM Backup b WHERE b.createdAt >= :since ORDER BY b.createdAt DESC")
    List<Backup> findBackupsSince(LocalDateTime since);
    
    @Query("SELECT b FROM Backup b WHERE b.clusterId = :clusterId AND b.status = 'COMPLETED' ORDER BY b.createdAt DESC")
    List<Backup> findCompletedBackupsByClusterId(Long clusterId);
    
    @Query("SELECT COUNT(b) FROM Backup b WHERE b.status = 'COMPLETED'")
    long countCompletedBackups();
    
    @Query("SELECT SUM(b.sizeBytes) FROM Backup b WHERE b.status = 'COMPLETED'")
    Long totalBackupSize();
}
