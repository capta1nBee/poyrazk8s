package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "persistent_volumes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersistentVolume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private String kind = "PersistentVolume";

    @Column(name = "api_version")
    private String apiVersion;

    @Column(name = "cluster_id", nullable = false)
    private Long clusterId;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String uid;

    @Column(name = "resource_version")
    private String resourceVersion;

    private Integer generation;

    @JsonRawValue
    @Column(columnDefinition = "TEXT")
    private String capacity; // JSON: {storage: "10Gi"}

    @JsonRawValue
    @Column(name = "access_modes", columnDefinition = "TEXT")
    private String accessModes; // JSON array

    @Column(name = "persistent_volume_reclaim_policy")
    private String persistentVolumeReclaimPolicy; // Retain, Recycle, Delete

    @Column(name = "storage_class_name")
    private String storageClassName;

    @Column(name = "volume_mode")
    private String volumeMode; // Filesystem, Block

    private String phase; // Available, Bound, Released, Failed
    private String message;
    private String reason;

    @JsonRawValue
    @Column(name = "claim_ref", columnDefinition = "TEXT")
    private String claimRef; // JSON: bound PVC reference

    @JsonRawValue
    @Column(name = "persistent_volume_source", columnDefinition = "TEXT")
    private String persistentVolumeSource; // JSON: nfs, hostPath, etc.

    @JsonRawValue
    @Column(name = "mount_options", columnDefinition = "TEXT")
    private String mountOptions; // JSON array

    @JsonRawValue
    @Column(name = "node_affinity", columnDefinition = "TEXT")
    private String nodeAffinity; // JSON

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
