package com.k8s.platform.domain.dto.appcreator;

import lombok.Data;

@Data
public class ContainerBuildRequest {
    /** Git connection UUID */
    private String gitConnectionId;
    /** Full repo path: "owner/repo" */
    private String repoPath;
    /** Branch to clone */
    private String branch;
    /** Path to Dockerfile inside the repo, e.g. "Dockerfile" or "docker/Dockerfile.prod" */
    private String dockerfilePath;
    /** Registry connection UUID */
    private String registryConnectionId;
    /** Application name – used to derive the image tag: appname_yyyyMMddHHmm */
    private String appName;
}

