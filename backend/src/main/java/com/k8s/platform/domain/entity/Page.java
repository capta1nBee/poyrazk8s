package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "resource_kind")
    private String resourceKind;

    @Column(name = "icon")
    private String icon;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_namespace_scoped")
    private Boolean isNamespaceScoped;

    /**
     * Permission tier:
     * 1 = Namespace + Name + Action (namespace-scoped K8s resources: Pod, Deployment, ...)
     * 2 = Name + Action             (cluster-scoped K8s resources: Node, ClusterRole, ...)
     * 3 = Page access only          (feature/management pages: Backup, Helm, ...)
     */
    @Column(name = "page_tier")
    private Integer pageTier;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
