package com.k8s.platform.domain.entity.appcreator;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appcreator_git_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Cluster this connection belongs to */
    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    /** Platform user who owns this connection */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 'github' or 'gitlab' */
    @Column(nullable = false, length = 50)
    private String provider;

    /** Human-readable label */
    @Column(nullable = false)
    private String name;

    /** Personal Access Token (plain; encrypt in Faz 4) */
    @Column(name = "access_token", nullable = false, length = 2000)
    private String accessToken;

    /**
     * Base URL for self-hosted GitLab instances.
     * Null means cloud: https://api.github.com or https://gitlab.com/api/v4
     */
    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

