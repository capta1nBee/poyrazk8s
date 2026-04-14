package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "hpas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorizontalPodAutoscaler implements BaseK8sEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String kind = "HorizontalPodAutoscaler";

    @Column(name = "api_version")
    private String apiVersion;

    @Column(name = "cluster_id", nullable = false)
    private Long clusterId;

    @Column(nullable = false)
    private String namespace;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String uid;

    @Column(name = "resource_version")
    private String resourceVersion;

    private Integer generation;

    // HPA-specific fields
    @Column(name = "scale_target_ref", columnDefinition = "TEXT")
    private String scaleTargetRef; // JSON: {kind, name, apiVersion}

    @Column(name = "min_replicas")
    private Integer minReplicas;

    @Column(name = "max_replicas")
    private Integer maxReplicas;

    @Column(name = "current_replicas")
    private Integer currentReplicas;

    @Column(name = "desired_replicas")
    private Integer desiredReplicas;

    @Column(columnDefinition = "TEXT")
    private String metrics; // JSON: spec.metrics array

    @Column(name = "current_metrics", columnDefinition = "TEXT")
    private String currentMetrics; // JSON: status.currentMetrics array

    @Column(columnDefinition = "TEXT")
    private String conditions; // JSON: status.conditions array

    @Column(columnDefinition = "TEXT")
    private String behavior; // JSON: spec.behavior

    // Common K8s metadata
    @Column(name = "owner_refs", columnDefinition = "TEXT")
    private String ownerRefs;

    @Column(columnDefinition = "TEXT")
    private String labels;

    @Column(columnDefinition = "TEXT")
    private String annotations;

    @Column(name = "managed_fields", columnDefinition = "TEXT")
    private String managedFields;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "k8s_created_at")
    private String k8sCreatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
