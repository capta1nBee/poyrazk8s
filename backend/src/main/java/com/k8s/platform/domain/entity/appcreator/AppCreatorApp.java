package com.k8s.platform.domain.entity.appcreator;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appcreator_apps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCreatorApp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private String namespace;

    @Column(name = "workload_type", nullable = false, length = 50)
    private String workloadType;

    // @Lob
    @Column(nullable = false)
    private String config;

    @Column(length = 50)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
