package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContainerBuildJobDto {
    private String jobId;
    /** PENDING | RUNNING | SUCCESS | FAILED */
    private String status;
    /** Full image reference that was pushed: registry/prefix/appname:tag */
    private String imageRef;
    private String logs;
    private String errorMessage;
}

