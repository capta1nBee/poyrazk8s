package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "subject_type", nullable = false)
    private String subjectType; // "user"

    @Column(name = "subject_name", nullable = false)
    private String subjectName; // email

    @Column(name = "assignments_json", nullable = false, columnDefinition = "TEXT")
    private String assignmentsJson; // JSON array

    @Column(name = "ui_permissions_json", columnDefinition = "TEXT")
    private String uiPermissionsJson; // JSON object

    @Column(name = "roles_json", columnDefinition = "TEXT")
    private String rolesJson; // JSON array

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
