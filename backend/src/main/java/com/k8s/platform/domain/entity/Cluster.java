package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "clusters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cluster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(unique = true)
    private String uid; // Unique identifier for cluster

    @Column(name = "api_server")
    private String apiServer;

    @Column(name = "auth_type", nullable = false)
    private String authType; // TOKEN | CERT | OIDC

    @Column(columnDefinition = "TEXT")
    private String kubeconfig; // Base64 encoded or encrypted

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column
    private String status; // Ready | NotReady | Unknown

    @Column
    private String version;

    @Column
    private Integer nodes;

    @Column
    private String cpu;

    @Column
    private String memory;

    @Column(name = "vuln_scan_enabled")
    @Builder.Default
    private Boolean vulnScanEnabled = false;

    @Column(name = "backup_enabled")
    @Builder.Default
    private Boolean backupEnabled = true;

    @Column(name = "private_registry_user")
    private String privateRegistryUser;

    @Column(name = "private_registry_password")
    private String privateRegistryPassword;

    @Column
    private String provider;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (uid == null || uid.isEmpty()) {
            uid = java.util.UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
