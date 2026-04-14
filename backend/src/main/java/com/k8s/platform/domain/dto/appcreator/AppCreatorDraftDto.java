package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AppCreatorDraftDto {
    private UUID id;
    private String clusterUid;
    private UUID appId;
    private String wizardState;
    private Integer currentStep;
    private LocalDateTime updatedAt;
}

