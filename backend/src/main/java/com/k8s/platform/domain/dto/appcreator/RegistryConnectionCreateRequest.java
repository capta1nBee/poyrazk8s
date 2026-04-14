package com.k8s.platform.domain.dto.appcreator;

import lombok.Data;

@Data
public class RegistryConnectionCreateRequest {
    private String registryType;   // "dockerhub" | "gitlab" | "github" | "custom"
    private String name;           // friendly label
    private String serverUrl;      // optional: custom registry URL
    private String username;
    private String passwordToken;  // PAT or password
    private String imagePrefix;    // namespace/org prefix
    private Boolean isDefault;
}

