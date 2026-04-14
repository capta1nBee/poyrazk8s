package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.ConfigMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigMapResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private Integer dataCount;
    private Boolean immutable;
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional UI fields
    private String labels;
    private String annotations;
    private String uid;

    public static ConfigMapResponseDTO fromEntity(ConfigMap configMap) {
        return ConfigMapResponseDTO.builder()
                .id(configMap.getId())
                .kind("ConfigMap")
                .clusterId(configMap.getClusterId())
                .namespace(configMap.getNamespace())
                .name(configMap.getName())
                .dataCount(configMap.getDataCount())
                .immutable(configMap.getImmutable())
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(configMap.getK8sCreatedAt() != null ? configMap.getK8sCreatedAt() :
                          (configMap.getCreatedAt() != null ? configMap.getCreatedAt().toString() : null))
                .updatedAt(configMap.getUpdatedAt() != null ? configMap.getUpdatedAt().toString() : null)
                .isDeleted(configMap.getIsDeleted() != null ? configMap.getIsDeleted() : false)
                .labels(configMap.getLabels())
                .annotations(configMap.getAnnotations())
                .uid(configMap.getUid())
                .build();
    }
}

