package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AppCreatorDeployResult {
    private UUID historyId;
    private String status;
    private String deployType;
    private String gitPrUrl;
    private String gitCommitSha;
    private Integer resourceCount;
    private String errorMessage;
    private LocalDateTime createdAt;
}

