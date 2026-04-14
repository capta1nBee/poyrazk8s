package com.k8s.platform.domain.entity.authorization;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ui_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UIPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pages", columnDefinition = "TEXT", nullable = false)
    private String pages; // JSON array: ["dashboard", "cluster", "namespace", "object", "action", "settings"]

    @Column(name = "features", columnDefinition = "TEXT")
    private String features; // JSON array: ["terminal", "logs", "metrics", "yaml-editor"]

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

