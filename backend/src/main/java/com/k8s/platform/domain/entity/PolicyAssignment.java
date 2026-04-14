package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "policy_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    @Column(nullable = false)
    private String namespace; // '*' for all namespaces

    @Column(name = "resource_kind", nullable = false)
    private String resourceKind;

    @Column(name = "name_pattern", nullable = false)
    private String namePattern; // supports wildcards: *, ?, a*b, *test*

    @Column(name = "actions_json", nullable = false, columnDefinition = "TEXT")
    private String actionsJson; // JSON array

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
