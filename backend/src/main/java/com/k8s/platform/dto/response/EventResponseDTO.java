package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.K8sEvent;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String involvedObjectKind;
    private String involvedObjectName;
    private String type; // Normal, Warning
    private String reason;
    private String message;
    private Integer count;
    private String lastSeen;
    private String createdAt;
    private String updatedAt;
    // Additional UI fields
    private String uid;
    private String source;

    public static EventResponseDTO fromEntity(K8sEvent event) {
        return EventResponseDTO.builder()
                .id(event.getId())
                .kind("Event")
                .clusterId(event.getClusterId())
                .namespace(event.getNamespace())
                .involvedObjectKind(event.getInvolvedObjectKind() != null ? event.getInvolvedObjectKind()
                        : extractInvolvedObjectKind(event.getInvolvedObject()))
                .involvedObjectName(event.getInvolvedObjectName() != null ? event.getInvolvedObjectName()
                        : extractInvolvedObjectName(event.getInvolvedObject()))
                .type(event.getType())
                .reason(event.getReason())
                .message(event.getMessage())
                .count(event.getCount())
                .lastSeen(event.getLastTimestamp() != null ? event.getLastTimestamp().toString() : null)
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .createdAt(event.getK8sCreatedAt() != null ? event.getK8sCreatedAt()
                        : (event.getCreatedAt() != null ? event.getCreatedAt().toString() : null))
                .updatedAt(event.getUpdatedAt() != null ? event.getUpdatedAt().toString() : null)
                .uid(event.getUid())
                .source(event.getSource())
                .build();
    }

    private static String extractInvolvedObjectKind(String involvedObjectJson) {
        if (involvedObjectJson == null || involvedObjectJson.isEmpty()) {
            return null;
        }
        try {
            if (involvedObjectJson.contains("\"kind\"")) {
                return involvedObjectJson.replaceAll(".*\"kind\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            }
        } catch (Exception e) {
            // Return null if parsing fails
        }
        return null;
    }

    private static String extractInvolvedObjectName(String involvedObjectJson) {
        if (involvedObjectJson == null || involvedObjectJson.isEmpty()) {
            return null;
        }
        try {
            if (involvedObjectJson.contains("\"name\"")) {
                return involvedObjectJson.replaceAll(".*\"name\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            }
        } catch (Exception e) {
            // Return null if parsing fails
        }
        return null;
    }
}
