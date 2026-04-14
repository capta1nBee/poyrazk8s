package com.k8s.platform.dto.request;

import lombok.Data;

@Data
public class AllowedCommandRequest {
    /** Casbin RoleTemplate name (e.g. "dev-operator"). Replaces legacy roleId. */
    private String roleTemplateName;
    private String commandPattern;
    private String description;
}
