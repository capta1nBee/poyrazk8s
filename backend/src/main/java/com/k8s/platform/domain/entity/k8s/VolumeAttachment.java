package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "volume_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumeAttachment implements BaseK8sEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String kind = "VolumeAttachment";

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

    private String attacher;

    @Column(name = "persistent_volume_name")
    private String persistentVolumeName;

    @Column(name = "node_name")
    private String nodeName;

    @Column(columnDefinition = "TEXT")
    private String source; // JSON

    private Boolean attached;

    @Column(columnDefinition = "TEXT")
    private String attachmentMetadata; // JSON

    @Column(name = "detach_error", columnDefinition = "TEXT")
    private String detachError; // JSON

    @Column(name = "attach_error", columnDefinition = "TEXT")
    private String attachError; // JSON

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
