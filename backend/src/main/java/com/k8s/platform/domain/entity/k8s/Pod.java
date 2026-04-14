package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pods")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String kind = "Pod";

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

    private String phase; // Pending, Running, Succeeded, Failed, Unknown

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "restart_count")
    private Integer restartCount = 0;

    @Column(name = "qos_class")
    private String qosClass; // BestEffort, Burstable, Guaranteed

    @Column(columnDefinition = "TEXT")
    private String containers; // JSON array

    @Column(columnDefinition = "TEXT")
    private String initContainers; // JSON array

    @Column(columnDefinition = "TEXT")
    private String conditions; // JSON array

    @Column(name = "owner_refs", columnDefinition = "TEXT")
    private String ownerRefs; // JSON

    @Column(columnDefinition = "TEXT")
    private String labels; // JSON

    @Column(columnDefinition = "TEXT")
    private String annotations; // JSON

    @Column(name = "managed_fields", columnDefinition = "TEXT")
    private String managedFields; // JSON

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "k8s_created_at")
    private String k8sCreatedAt; // Kubernetes metadata.creationTimestamp

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
