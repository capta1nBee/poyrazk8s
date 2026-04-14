package com.k8s.platform.domain.dto.appcreator;

import lombok.Data;

@Data
public class GitConnectionCreateRequest {
    private String provider;     // "github" | "gitlab"
    private String name;         // friendly label
    private String accessToken;  // PAT
    private String baseUrl;      // optional: self-hosted GitLab base URL
    private Boolean isDefault;
}

