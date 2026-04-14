package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RegistryConnectionDto {
    private UUID id;
    private String registryType;
    private String name;
    private String serverUrl;
    private String username;
    private String imagePrefix;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    // passwordToken intentionally NOT included
}

