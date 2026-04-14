package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AppCreatorTemplateDto {
    private UUID id;
    private String clusterUid;
    private String name;
    private String description;
    private String category;
    private String icon;
    private String config;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

