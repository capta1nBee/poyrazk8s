package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "priority_level_configurations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorityLevelConfiguration implements BaseK8sEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String kind = "PriorityLevelConfiguration";

    @Column(name = "api_version")
    private String apiVersion;

    @Column(name = "cluster_id", nullable = false)
    private Long clusterId;

    @Column(nullable = false)
    private String name;

    private String namespace; // Cluster-scoped resource, always null

    @Column(unique = true)
    private String uid;

    @Column(name = "resource_version")
    private String resourceVersion;

    private Integer generation;

    @Column(name = "owner_refs", columnDefinition = "TEXT")
    private String ownerRefs;

    @Column(name = "managed_fields", columnDefinition = "TEXT")
    private String managedFields;

    private String limited; // Limited or Exempt

    @Column(name = "nominal_concurrency_shares")
    private Integer nominalConcurrencyShares;

    private Integer queues;

    @Column(name = "hand_size")
    private Integer handSize;

    @Column(name = "queue_length_limit")
    private Integer queueLengthLimit;

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
