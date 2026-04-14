package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "security_rules", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"cluster_uid", "name"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"createdByUser"})
public class SecurityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_uid", nullable = false, length = 500)
    private String clusterUid;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String priority; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(name = "condition_json", nullable = false, columnDefinition = "TEXT")
    private String conditionJson; // JSON object representing condition

    @Column(columnDefinition = "TEXT")
    private String output; // Output message template

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson; // JSON array of tags

    @Column(name = "rule_type", length = 50)
    @Builder.Default
    private String ruleType = "process"; // process, network, file, file_access, etc.

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdByUser;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void toggleEnabled() {
        this.enabled = !this.enabled;
        this.updatedAt = LocalDateTime.now();
    }
}
