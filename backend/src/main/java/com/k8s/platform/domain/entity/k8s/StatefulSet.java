package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "statefulsets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatefulSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String kind = "StatefulSet";

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

    @Column(name = "ready_replicas")
    private Integer readyReplicas;

    @Column(name = "current_replicas")
    private Integer currentReplicas;

    @Column(name = "updated_replicas")
    private Integer updatedReplicas;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "update_strategy", columnDefinition = "TEXT")
    private String updateStrategy; // JSON

    @Column(name = "owner_refs", columnDefinition = "TEXT")
    private String ownerRefs; // JSON

    @Column(columnDefinition = "TEXT")
    private String labels; // JSON

    @Column(columnDefinition = "TEXT")
    private String annotations; // JSON

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
