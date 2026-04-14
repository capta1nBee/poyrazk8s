package com.k8s.platform.domain.entity.appcreator;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appcreator_deploy_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCreatorDeployHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "app_id", nullable = false)
    private UUID appId;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    @Column(name = "deploy_type", nullable = false, length = 50)
    private String deployType;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "git_repo")
    private String gitRepo;

    @Column(name = "git_branch")
    private String gitBranch;

    @Column(name = "git_pr_url", length = 500)
    private String gitPrUrl;

    @Column(name = "git_commit_sha", length = 100)
    private String gitCommitSha;

    @Column(name = "resource_count")
    private Integer resourceCount;

    // @Lob
    @Column(name = "yaml_snapshot")
    private String yamlSnapshot;

    // @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "deployed_by")
    private Long deployedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
