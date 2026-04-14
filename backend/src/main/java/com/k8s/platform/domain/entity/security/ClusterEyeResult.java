package com.k8s.platform.domain.entity.security;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Persists the result of a ClusterEye security scan for a single workload.
 *
 * Natural key: (cluster_uid, namespace, workload_kind, workload_name)
 * Periodic scans UPDATE this row in-place — no duplicate rows per workload.
 */
@Entity
@Table(
    name = "cluster_eye_results",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_eye_workload",
        columnNames = {"cluster_uid", "namespace", "workload_kind", "workload_name"}
    )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClusterEyeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_uid", nullable = false, length = 255)
    private String clusterUid;

    @Column(name = "namespace", nullable = false, length = 255)
    private String namespace;

    /** Deployment | StatefulSet | DaemonSet | CronJob | Pod */
    @Column(name = "workload_kind", nullable = false, length = 50)
    private String workloadKind;

    @Column(name = "workload_name", nullable = false, length = 255)
    private String workloadName;

    /** JSON-serialised {@code List<String>} of finding messages */
    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @Column(name = "critical_count", nullable = false)
    private int criticalCount;

    @Column(name = "high_count", nullable = false)
    private int highCount;

    @Column(name = "medium_count", nullable = false)
    private int mediumCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "last_scanned_at", nullable = false)
    private LocalDateTime lastScannedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
