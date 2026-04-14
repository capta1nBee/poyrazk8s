package com.k8s.platform.domain.entity.federation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "federation_resources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FederationResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "federation_id", nullable = false)
    private Federation federation;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String namespace;

    @Column(nullable = false)
    private String name;

    @Column(name = "sync_status")
    private String syncStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "last_error_time")
    private LocalDateTime lastErrorTime;

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    @Column(name = "previous_state_yaml", columnDefinition = "TEXT")
    private String previousStateYaml;

    @Column(name = "dependency_status", columnDefinition = "TEXT")
    private String dependencyStatus;

    @Column(name = "backup_yaml", columnDefinition = "TEXT")
    private String backupYaml;
}
