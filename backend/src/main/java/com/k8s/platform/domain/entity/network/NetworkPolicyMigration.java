package com.k8s.platform.domain.entity.network;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "network_policy_migrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkPolicyMigration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private String action; // create, update, delete, rollback

    @Column(name = "previous_yaml", columnDefinition = "TEXT")
    private String previousYaml;

    @Column(name = "new_yaml", columnDefinition = "TEXT")
    private String newYaml;

    @Column(name = "change_description", columnDefinition = "TEXT")
    private String changeDescription;

    @Column(name = "applied_by")
    private String appliedBy;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "rollback_at")
    private LocalDateTime rollbackAt;

    @Column(name = "rolled_back_by")
    private String rolledBackBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (appliedAt == null) {
            appliedAt = LocalDateTime.now();
        }
    }
}
