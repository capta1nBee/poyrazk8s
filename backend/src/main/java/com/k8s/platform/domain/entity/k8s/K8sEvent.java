package com.k8s.platform.domain.entity.k8s;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "k8s_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class K8sEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private String kind = "Event";

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

    private String reason;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String type; // Normal, Warning

    private Integer count;

    @Column(name = "first_timestamp")
    private LocalDateTime firstTimestamp;

    @Column(name = "last_timestamp")
    private LocalDateTime lastTimestamp;

    @Column(name = "involved_object_kind")
    private String involvedObjectKind;

    @Column(name = "involved_object_name")
    private String involvedObjectName;

    @Column(name = "involved_object", columnDefinition = "TEXT")
    private String involvedObject; // JSON

    @Column(name = "last_seen")
    private String lastSeen;

    @Column(columnDefinition = "TEXT")
    private String source; // JSON

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
