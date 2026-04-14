package com.k8s.platform.domain.entity.network;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_network_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedNetworkPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    @Column(nullable = false)
    private String namespace;

    @Column(nullable = false)
    private String name;

    @Column(name = "policy_type", nullable = false)
    private String policyType; // 'ingress' or 'egress'

    @Column(name = "pod_selector", nullable = false, columnDefinition = "TEXT")
    private String podSelector; // JSON format

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rules; // JSON array of rules

    @Column(name = "yaml_content", nullable = false, columnDefinition = "TEXT")
    private String yamlContent;

    @Column(nullable = false)
    @Builder.Default
    private String status = "draft"; // draft, applied, deleted

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
