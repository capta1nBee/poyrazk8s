package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "security_alerts", indexes = {
    @Index(name = "idx_cluster_alert", columnList = "cluster_uid,created_at"),
    @Index(name = "idx_priority_alert", columnList = "priority"),
    @Index(name = "idx_pod_alert", columnList = "pod_name"),
    @Index(name = "idx_namespace_alert", columnList = "namespace_name"),
    @Index(name = "idx_fingerprint", columnList = "fingerprint"),
    @Index(name = "idx_resolved", columnList = "resolved"),
    @Index(name = "idx_acknowledged", columnList = "is_acknowledged")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"acknowledgedByUser", "resolvedByUser"})
public class SecurityAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_uid", nullable = false, length = 500)
    private String clusterUid;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // execve, open, connect, etc.

    @Column(nullable = false, length = 20)
    private String priority; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "rule_description", columnDefinition = "TEXT")
    private String ruleDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String output; // Alert message

    @Column(name = "namespace_name", length = 255)
    private String namespaceName;

    @Column(name = "pod_name", length = 255)
    private String podName;

    @Column(name = "container_id", length = 255)
    private String containerId;

    @Column(name = "event_data_json", columnDefinition = "TEXT")
    private String eventDataJson; // Full event data as JSON

    @Column(name = "fingerprint", length = 255)
    private String fingerprint; // For deduplication

    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson; // JSON array of tags

    @Column(name = "is_acknowledged", nullable = false)
    @Builder.Default
    private Boolean isAcknowledged = false;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledgment_note", columnDefinition = "TEXT")
    private String acknowledgmentNote;

    @Column(nullable = false)
    @Builder.Default
    private Boolean resolved = false;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by", insertable = false, updatable = false)
    private User acknowledgedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by", insertable = false, updatable = false)
    private User resolvedByUser;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void acknowledge(Long userId, String note) {
        this.isAcknowledged = true;
        this.acknowledgedBy = userId;
        this.acknowledgedAt = LocalDateTime.now();
        this.acknowledgmentNote = note;
    }

    public void resolve(Long userId, String note) {
        this.resolved = true;
        this.resolvedBy = userId;
        this.resolvedAt = LocalDateTime.now();
        this.resolutionNote = note;
    }

    public boolean isPending() {
        return !isAcknowledged && !resolved;
    }
}
