package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "actions", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "page_id", "action_code" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "resource_kind")
    private String resourceKind;

    @Column(name = "requires_write")
    private Boolean requiresWrite;

    @Column(name = "is_dangerous")
    private Boolean isDangerous;

    @Column(name = "icon")
    private String icon;

    @Column(name = "category")
    private String category;

    @Column(name = "is_active")
    private Boolean isActive;

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
        if (requiresWrite == null) {
            requiresWrite = false;
        }
        if (isDangerous == null) {
            isDangerous = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
