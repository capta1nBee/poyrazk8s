package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AppCreatorAppDto {
    private UUID id;
    private String clusterUid;
    private String name;
    private String description;
    private String namespace;
    private String workloadType;
    private String config;
    private String status;
    private UUID templateId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

