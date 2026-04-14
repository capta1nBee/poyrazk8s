package com.k8s.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionRequest {
    private Long pageId;
    private String name;
    private String displayName;
    private String description;
    private String actionCode;
    private String resourceKind;
    private Boolean requiresWrite;
    private Boolean isDangerous;
    private String icon;
    private Boolean isActive;
}
