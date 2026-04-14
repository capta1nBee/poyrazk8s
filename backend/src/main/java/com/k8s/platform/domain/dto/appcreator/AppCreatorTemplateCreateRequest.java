package com.k8s.platform.domain.dto.appcreator;

import lombok.Data;

@Data
public class AppCreatorTemplateCreateRequest {
    private String name;
    private String description;
    private String category;
    private String icon;
    private String config;
    private Boolean isPublic;
}

