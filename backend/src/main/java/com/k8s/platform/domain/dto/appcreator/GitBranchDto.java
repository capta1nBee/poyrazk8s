package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitBranchDto {
    private String name;
    private String commitSha;
    private Boolean isProtected;
}

