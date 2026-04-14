package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.StatefulSet;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatefulSetResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private Integer replicasDesired;
    private Integer replicasReady;
    private String serviceName;
    private String updateStrategy; // JSON or extracted type
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional UI fields
    private Integer replicas;
    private Integer currentReplicas;
    private Integer updatedReplicas;
    private String labels;
    private String annotations;

    public static StatefulSetResponseDTO fromEntity(StatefulSet statefulSet) {
        return StatefulSetResponseDTO.builder()
                .id(statefulSet.getId())
                .kind("StatefulSet")
                .clusterId(statefulSet.getClusterId())
                .namespace(statefulSet.getNamespace())
                .name(statefulSet.getName())
                .replicasDesired(statefulSet.getReplicas())
                .replicasReady(statefulSet.getReadyReplicas())
                .serviceName(statefulSet.getServiceName())
                .updateStrategy(extractUpdateStrategyType(statefulSet.getUpdateStrategy()))
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(statefulSet.getK8sCreatedAt() != null ? statefulSet.getK8sCreatedAt() :
                          (statefulSet.getCreatedAt() != null ? statefulSet.getCreatedAt().toString() : null))
                .updatedAt(statefulSet.getUpdatedAt() != null ? statefulSet.getUpdatedAt().toString() : null)
                .isDeleted(statefulSet.getIsDeleted() != null ? statefulSet.getIsDeleted() : false)
                .replicas(statefulSet.getReplicas())
                .currentReplicas(statefulSet.getCurrentReplicas())
                .updatedReplicas(statefulSet.getUpdatedReplicas())
                .labels(statefulSet.getLabels())
                .annotations(statefulSet.getAnnotations())
                .build();
    }
    
    private static String extractUpdateStrategyType(String updateStrategyJson) {
        if (updateStrategyJson == null || updateStrategyJson.isEmpty()) {
            return "RollingUpdate";
        }
        try {
            if (updateStrategyJson.contains("\"type\"")) {
                return updateStrategyJson.replaceAll(".*\"type\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            }
        } catch (Exception e) {
            // Return default if parsing fails
        }
        return "RollingUpdate";
    }
}

