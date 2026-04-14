package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.Pod;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PodResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private String phase;
    private String nodeName;
    private String owner; // Extracted from ownerReferences
    private Integer restartCount;
    private String qosClass;
    private String k8sCreatedAt; // K8s creation timestamp
    private String updatedAt;
    private Boolean isDeleted;
    // Additional fields for UI
   // private String containers;
   // private String ownerReferences;
   // private String labels;
   // private String annotations;

    public static PodResponseDTO fromEntity(Pod pod) {
        // Extract owner from ownerReferences JSON
        String owner = extractOwner(pod.getOwnerRefs());

        return PodResponseDTO.builder()
                .id(pod.getId())
                .kind("Pod")
                .clusterId(pod.getClusterId())
                .namespace(pod.getNamespace())
                .name(pod.getName())
                .phase(pod.getPhase())
                .nodeName(pod.getNodeName())
                .owner(owner)
                .restartCount(pod.getRestartCount() != null ? pod.getRestartCount() : 0)
                .qosClass(pod.getQosClass())
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(pod.getK8sCreatedAt() != null ? pod.getK8sCreatedAt() :
                          (pod.getCreatedAt() != null ? pod.getCreatedAt().toString() : null))
                .updatedAt(pod.getUpdatedAt() != null ? pod.getUpdatedAt().toString() : null)
                .isDeleted(pod.getIsDeleted() != null ? pod.getIsDeleted() : false)
                //.containers(pod.getContainers())
                //.ownerReferences(pod.getOwnerRefs())
                //.labels(pod.getLabels())
                //.annotations(pod.getAnnotations())
                .build();
    }

    private static String extractOwner(String ownerRefsJson) {
        if (ownerRefsJson == null || ownerRefsJson.isEmpty() || ownerRefsJson.equals("[]")) {
            return null;
        }
        try {
            // Simple extraction - assumes first owner reference
            // Format: [{"kind":"Deployment","name":"app-deployment",...}]
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
