package com.k8s.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {
    private String name;
    private String displayName;
    private String description;
    private String resourceKind;
    private String icon;
    private Boolean isActive;
    /** Whether this page is namespace-scoped (Tier 1). */
    private Boolean isNamespaceScoped;
    /** Permission tier: 1=Namespace+Name+Action, 2=Name+Action, 3=Page-only */
    private Integer pageTier;
}
