package com.k8s.platform.dto.response;

import com.k8s.platform.domain.entity.Page;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse {
    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String resourceKind;
    private String icon;
    private Boolean isActive;
    private Boolean isNamespaceScoped;
    private Integer pageTier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ActionResponse> actions;

    public static PageResponse fromEntity(Page page) {
        return PageResponse.builder()
                .id(page.getId())
                .name(page.getName())
                .displayName(page.getDisplayName())
                .description(page.getDescription())
                .resourceKind(page.getResourceKind())
                .icon(page.getIcon())
                .isActive(page.getIsActive())
                .isNamespaceScoped(page.getIsNamespaceScoped())
                .pageTier(page.getPageTier())
                .createdAt(page.getCreatedAt())
                .updatedAt(page.getUpdatedAt())
                .build();
    }
}
