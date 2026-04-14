package com.k8s.platform.domain.entity.appcreator;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appcreator_registry_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistryConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Cluster this connection belongs to */
    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    /** Platform user who owns this connection */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 'dockerhub' | 'gitlab' | 'github' | 'custom' */
    @Column(name = "registry_type", nullable = false, length = 50)
    private String registryType;

    /** Human-readable label */
    @Column(nullable = false)
    private String name;

    /** Registry server URL – null means Docker Hub (index.docker.io) */
    @Column(name = "server_url", length = 500)
    private String serverUrl;

    @Column(nullable = false, length = 255)
    private String username;

    /** PAT or password */
    @Column(name = "password_token", nullable = false, length = 2000)
    private String passwordToken;

    /** Namespace/org prefix for image names, e.g. "myorg" → myorg/appname:tag */
    @Column(name = "image_prefix", length = 255)
    private String imagePrefix;

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

