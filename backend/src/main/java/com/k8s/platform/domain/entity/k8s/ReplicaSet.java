package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "replicasets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicaSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false)
    private String kind = "ReplicaSet";

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

    private Integer replicas;

    @Column(name = "min_ready_seconds")
    private Integer minReadySeconds;

    @Column(name = "available_replicas")
    private Integer availableReplicas;

    @Column(name = "ready_replicas")
    private Integer readyReplicas;

    @Column(name = "fully_labeled_replicas")
    private Integer fullyLabeledReplicas;

    @Column(name = "observed_generation")
    private Integer observedGeneration;

    @JsonRawValue
    @Column(columnDefinition = "TEXT")
    private String conditions; // JSON

    @JsonRawValue
    @Column(name = "owner_refs", columnDefinition = "TEXT")
    private String ownerRefs; // JSON

    @JsonRawValue
    @Column(columnDefinition = "TEXT")
    private String labels; // JSON

    @JsonRawValue
    @Column(columnDefinition = "TEXT")
    private String annotations; // JSON

    @JsonRawValue
    @Column(name = "managed_fields", columnDefinition = "TEXT")
    private String managedFields; // JSON

    @Builder.Default
    @Column(name = "is_deleted")
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
