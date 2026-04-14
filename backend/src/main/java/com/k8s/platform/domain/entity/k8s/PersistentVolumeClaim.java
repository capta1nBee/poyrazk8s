package com.k8s.platform.domain.entity.k8s;

import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "persistent_volume_claims")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersistentVolumeClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private String kind = "PersistentVolumeClaim";

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

    @Column(columnDefinition = "TEXT")
    private String capacity; // JSON

    @JsonRawValue
    @Column(name = "access_modes", columnDefinition = "TEXT")
    private String accessModes; // JSON array

    @JsonRawValue
    @Column(columnDefinition = "TEXT")
    private String resources; // JSON: {requests: {storage: "5Gi"}}

    @Column(name = "storage_class_name")
    private String storageClassName;

    @Column(name = "volume_mode")
    private String volumeMode;

    @Column(name = "volume_name")
    private String volumeName; // Bound PV name

    private String phase; // Pending, Bound, Lost

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

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
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
