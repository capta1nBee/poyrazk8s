package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.Deployment;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private Integer replicasDesired;
    private Integer replicasAvailable;
    private Integer replicasReady;
    private String strategy; // RollingUpdate | Recreate
    private Boolean paused;
    private String owner; // Extracted from ownerReferences
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional fields for UI
    private Integer replicas;
    private Integer updatedReplicas;
    private String containers;
    private String ownerReferences;
    private String labels;
    private String annotations;

    public static DeploymentResponseDTO fromEntity(Deployment deployment) {
        String owner = extractOwner(deployment.getOwnerRefs());

        return DeploymentResponseDTO.builder()
                .id(deployment.getId())
                .kind("Deployment")
                .clusterId(deployment.getClusterId())
                .namespace(deployment.getNamespace())
                .name(deployment.getName())
                .replicasDesired(deployment.getDesiredReplicas())
                .replicasAvailable(deployment.getAvailableReplicas())
                .replicasReady(deployment.getReadyReplicas())
                .strategy(deployment.getStrategyType())
                .paused(deployment.getPaused())
                .owner(owner)
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(deployment.getK8sCreatedAt() != null ? deployment.getK8sCreatedAt() :
                          (deployment.getCreatedAt() != null ? deployment.getCreatedAt().toString() : null))
                .updatedAt(deployment.getUpdatedAt() != null ? deployment.getUpdatedAt().toString() : null)
                .isDeleted(deployment.getIsDeleted() != null ? deployment.getIsDeleted() : false)
                .replicas(deployment.getReplicas())
                .updatedReplicas(deployment.getUpdatedReplicas())
                .containers(deployment.getContainers())
                .ownerReferences(deployment.getOwnerRefs())
                .labels(deployment.getLabels())
                .annotations(deployment.getAnnotations())
                .build();
    }

    private static String extractOwner(String ownerRefsJson) {
        if (ownerRefsJson == null || ownerRefsJson.isEmpty() || ownerRefsJson.equals("[]")) {
            return null;
        }
        try {
            if (ownerRefsJson.contains("\"kind\"")) {
                String kind = ownerRefsJson.replaceAll(".*\"kind\"\\s*:\\s*\"([^\"]+)\".*", "$1");
                String name = ownerRefsJson.replaceAll(".*\"name\"\\s*:\\s*\"([^\"]+)\".*", "$1");
                return kind + "/" + name;
            }
        } catch (Exception e) {
            // Return null if parsing fails
        }
        return null;
    }
}
