package com.k8s.platform.dto.response;

import com.k8s.platform.domain.entity.SecurityAlert;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SecurityAlertResponse {
    private Long id;
    private String clusterUid;
    private String eventType;
    private String priority;
    private String ruleName;
    private String ruleDescription;
    private String output;
    private String namespaceName;
    private String podName;
    private String containerId;
    private Map<String, Object> eventData;
    private String fingerprint;
    private List<String> tags;
    private Boolean isAcknowledged;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String acknowledgmentNote;
    private Boolean resolved;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNote;
    private LocalDateTime createdAt;

    public static SecurityAlertResponse fromEntity(SecurityAlert alert) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> eventData = null;
            if (alert.getEventDataJson() != null) {
                eventData = mapper.readValue(alert.getEventDataJson(), Map.class);
            }

            List<String> tags = null;
            if (alert.getTagsJson() != null) {
                tags = mapper.readValue(alert.getTagsJson(), List.class);
            }

            return SecurityAlertResponse.builder()
                    .id(alert.getId())
                    .clusterUid(alert.getClusterUid())
                    .eventType(alert.getEventType())
                    .priority(alert.getPriority())
                    .ruleName(alert.getRuleName())
                    .ruleDescription(alert.getRuleDescription())
                    .output(alert.getOutput())
                    .namespaceName(alert.getNamespaceName())
                    .podName(alert.getPodName())
                    .containerId(alert.getContainerId())
                    .eventData(eventData)
                    .fingerprint(alert.getFingerprint())
                    .tags(tags)
                    .isAcknowledged(alert.getIsAcknowledged())
                    .acknowledgedBy(alert.getAcknowledgedByUser() != null ? alert.getAcknowledgedByUser().getUsername() : null)
                    .acknowledgedAt(alert.getAcknowledgedAt())
                    .acknowledgmentNote(alert.getAcknowledgmentNote())
                    .resolved(alert.getResolved())
                    .resolvedBy(alert.getResolvedByUser() != null ? alert.getResolvedByUser().getUsername() : null)
                    .resolvedAt(alert.getResolvedAt())
                    .resolutionNote(alert.getResolutionNote())
                    .createdAt(alert.getCreatedAt())
                    .build();
        } catch (Exception e) {
            // Log error but return basic response
            return SecurityAlertResponse.builder()
                    .id(alert.getId())
                    .clusterUid(alert.getClusterUid())
                    .eventType(alert.getEventType())
                    .priority(alert.getPriority())
                    .ruleName(alert.getRuleName())
                    .output(alert.getOutput())
                    .namespaceName(alert.getNamespaceName())
                    .podName(alert.getPodName())
                    .isAcknowledged(alert.getIsAcknowledged())
                    .resolved(alert.getResolved())
                    .createdAt(alert.getCreatedAt())
                    .build();
        }
    }
}
