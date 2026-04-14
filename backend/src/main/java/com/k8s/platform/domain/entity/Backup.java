package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "backups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Backup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_id", nullable = false)
    private Long clusterId;

    @Column(name = "cluster_name", nullable = false)
    private String clusterName;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    @Column(nullable = false)
    private String status; // RUNNING, COMPLETED, FAILED

    @Column(name = "backup_path")
    private String backupPath;

    @Column(name = "total_resources")
    private Integer totalResources;

    @Column(name = "total_namespaces")
    private Integer totalNamespaces;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "triggered_by")
    private String triggeredBy; // SCHEDULED, MANUAL

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
