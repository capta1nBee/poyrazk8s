package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "monitoring_configs", uniqueConstraints = {
    @UniqueConstraint(columnNames = "cluster_uid")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"updatedByUser"})
public class MonitoringConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cluster_uid", nullable = false, unique = true, length = 500)
    private String clusterUid;

    @Builder.Default
    @Column(name = "enable_execve")
    private Boolean enableExecve = false;

    @Builder.Default
    @Column(name = "enable_open")
    private Boolean enableOpen = false;

    @Builder.Default
    @Column(name = "enable_openat")
    private Boolean enableOpenat = false;

    @Builder.Default
    @Column(name = "enable_connect")
    private Boolean enableConnect = false;

    @Builder.Default
    @Column(name = "enable_bind")
    private Boolean enableBind = false;

    @Builder.Default
    @Column(name = "enable_unlink")
    private Boolean enableUnlink = false;

    @Builder.Default
    @Column(name = "enable_unlinkat")
    private Boolean enableUnlinkat = false;

    @Builder.Default
    @Column(name = "enable_write")
    private Boolean enableWrite = false;

    @Builder.Default
    @Column(name = "enable_link")
    private Boolean enableLink = false;

    @Builder.Default
    @Column(name = "enable_rename")
    private Boolean enableRename = false;

    @Builder.Default
    @Column(name = "enable_mkdir")
    private Boolean enableMkdir = false;

    @Builder.Default
    @Column(name = "enable_rmdir")
    private Boolean enableRmdir = false;

    @Builder.Default
    @Column(name = "enable_xattr")
    private Boolean enableXattr = false;

    @Builder.Default
    @Column(name = "enable_clone")
    private Boolean enableClone = false;

    @Builder.Default
    @Column(name = "enable_fork")
    private Boolean enableFork = false;

    @Builder.Default
    @Column(name = "enable_accept")
    private Boolean enableAccept = false;

    @Builder.Default
    @Column(name = "enable_ptrace")
    private Boolean enablePtrace = false;

    @Builder.Default
    @Column(name = "enable_mount")
    private Boolean enableMount = false;

    @Column(name = "additional_config_json", columnDefinition = "TEXT")
    private String additionalConfigJson; // For additional configuration options

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", insertable = false, updatable = false)
    private User updatedByUser;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateConfiguration(MonitoringConfig newConfig, Long userId) {
        this.enableExecve = newConfig.getEnableExecve();
        this.enableOpen = newConfig.getEnableOpen();
        this.enableOpenat = newConfig.getEnableOpenat();
        this.enableConnect = newConfig.getEnableConnect();
        this.enableBind = newConfig.getEnableBind();
        this.enableUnlink = newConfig.getEnableUnlink();
        this.enableUnlinkat = newConfig.getEnableUnlinkat();
        this.enableWrite = newConfig.getEnableWrite();
        this.enableLink = newConfig.getEnableLink();
        this.enableRename = newConfig.getEnableRename();
        this.enableMkdir = newConfig.getEnableMkdir();
        this.enableRmdir = newConfig.getEnableRmdir();
        this.enableXattr = newConfig.getEnableXattr();
        this.enableClone = newConfig.getEnableClone();
        this.enableFork = newConfig.getEnableFork();
        this.enableAccept = newConfig.getEnableAccept();
        this.enablePtrace = newConfig.getEnablePtrace();
        this.enableMount = newConfig.getEnableMount();
        this.additionalConfigJson = newConfig.getAdditionalConfigJson();
        this.updatedBy = userId;
        this.updatedAt = LocalDateTime.now();
    }
}
