package com.k8s.platform.dto.response;

import com.k8s.platform.domain.entity.Action;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResponse {
    private Long id;
    private Long pageId;
    private String pageName;
    private String name;
    private String displayName;
    private String description;
    private String actionCode;
    private String resourceKind;
    private Boolean requiresWrite;
    private Boolean isDangerous;
    private String icon;
    private String category;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ActionResponse fromEntity(Action action) {
        return ActionResponse.builder()
                .id(action.getId())
                .pageId(action.getPage() != null ? action.getPage().getId() : null)
                .pageName(action.getPage() != null ? action.getPage().getName() : null)
                .name(action.getName())
                .displayName(action.getDisplayName())
                .description(action.getDescription())
                .actionCode(action.getActionCode())
                .resourceKind(action.getResourceKind())
                .requiresWrite(action.getRequiresWrite())
                .isDangerous(action.getIsDangerous())
                .icon(action.getIcon())
                .category(action.getCategory())
                .isActive(action.getIsActive())
                .createdAt(action.getCreatedAt())
                .updatedAt(action.getUpdatedAt())
                .build();
    }
}
