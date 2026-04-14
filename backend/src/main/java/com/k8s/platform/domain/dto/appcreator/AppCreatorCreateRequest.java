package com.k8s.platform.domain.dto.appcreator;

import lombok.Data;

import java.util.UUID;

@Data
public class AppCreatorCreateRequest {
    private String name;
    private String description;
    private String namespace;
    private String workloadType;
    private String config;
    private UUID templateId;
}

