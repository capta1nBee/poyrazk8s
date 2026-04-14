package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "replication_controllers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicationController implements BaseK8sEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String kind = "ReplicationController";

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

    @Column(name = "owner_refs", columnDefinition = "TEXT")
    private String ownerRefs;

    @Column(name = "managed_fields", columnDefinition = "TEXT")
    private String managedFields;

    @Column(name = "desired_replicas")
    private Integer desiredReplicas;

    @Column(name = "current_replicas")
    private Integer currentReplicas;

    @Column(name = "ready_replicas")
    private Integer readyReplicas;

    @Column(name = "available_replicas")
    private Integer availableReplicas;

    @Column(name = "fully_labeled_replicas")
    private Integer fullyLabeledReplicas;

    @Column(name = "observed_generation")
    private Integer observedGeneration;

    @Column(columnDefinition = "TEXT")
    private String selector; // JSON

    @Column(columnDefinition = "TEXT")
    private String template; // JSON - pod template

    @Column(columnDefinition = "TEXT")
    private String conditions; // JSON array

    @Column(columnDefinition = "TEXT")
    private String labels; // JSON

    @Column(columnDefinition = "TEXT")
    private String annotations; // JSON

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
