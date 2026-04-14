package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.PersistentVolumeClaim;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PVCResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private String status; // Bound, Pending, Lost
    private String storageClass;
    private String requestedSize;
    private String boundVolume;
    private String accessModes;
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional UI fields
    private String labels;
    private String annotations;
    private String uid;
    private String resourceVersion;

    public static PVCResponseDTO fromEntity(PersistentVolumeClaim pvc) {
        return PVCResponseDTO.builder()
                .id(pvc.getId())
                .kind("PersistentVolumeClaim")
                .clusterId(pvc.getClusterId())
                .namespace(pvc.getNamespace())
                .name(pvc.getName())
                .status(pvc.getPhase())
                .storageClass(pvc.getStorageClassName())
                .requestedSize(extractRequestedSize(pvc.getResources()))
                .boundVolume(pvc.getVolumeName())
                .accessModes(extractAccessModes(pvc.getAccessModes()))
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(pvc.getK8sCreatedAt() != null ? pvc.getK8sCreatedAt() :
                          (pvc.getCreatedAt() != null ? pvc.getCreatedAt().toString() : null))
                .updatedAt(pvc.getUpdatedAt() != null ? pvc.getUpdatedAt().toString() : null)
                .isDeleted(pvc.getIsDeleted() != null ? pvc.getIsDeleted() : false)
                .labels(pvc.getLabels())
                .annotations(pvc.getAnnotations())
                .uid(pvc.getUid())
                .resourceVersion(pvc.getResourceVersion())
                .build();
    }

    private static String extractRequestedSize(String resourcesJson) {
        if (resourcesJson == null || resourcesJson.isEmpty()) {
            return null;
        }
        try {
            // Extract storage from resources.requests.storage
            if (resourcesJson.contains("\"storage\"")) {
                return resourcesJson.replaceAll(".*\"storage\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            }
        } catch (Exception e) {
            // Return null if parsing fails
        }
        return null;
    }

    private static String extractAccessModes(String accessModesJson) {
        if (accessModesJson == null || accessModesJson.isEmpty() || accessModesJson.equals("[]")) {
            return null;
        }
        try {
            // Simple extraction - convert JSON array to comma-separated string
            String modes = accessModesJson.replaceAll("[\\[\\]\"]", "");
            return modes.replace(",", ", ");
        } catch (Exception e) {
            // Return null if parsing fails
        }
        return null;
    }
}

