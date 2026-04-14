package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitRepoDto {
    private String fullName;      // "owner/repo"
    private String name;
    private String description;
    private String defaultBranch;
    private Boolean isPrivate;
    private String htmlUrl;
}

