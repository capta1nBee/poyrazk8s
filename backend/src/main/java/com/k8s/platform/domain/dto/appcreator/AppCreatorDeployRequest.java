package com.k8s.platform.domain.dto.appcreator;

import lombok.Data;

import java.util.UUID;

@Data
public class AppCreatorDeployRequest {
    private String deployType;       // "direct" or "git"
    private UUID   gitConnectionId;  // Phase 2: stored git connection
    private String gitRepo;          // "owner/repo" or "group/project"
    private String gitBranch;        // base branch (e.g. "main")
    private String gitPath;          // directory inside repo (default "k8s")
    /** @deprecated Phase 1 inline token — prefer gitConnectionId in Phase 2 */
    @Deprecated
    private String gitToken;
}

