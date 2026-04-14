package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.Secret;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private String type;
    private Integer dataCount;
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional UI fields
    private String labels;
    private String annotations;
    private String uid;

    public static SecretResponseDTO fromEntity(Secret secret) {
        return SecretResponseDTO.builder()
                .id(secret.getId())
                .kind("Secret")
                .clusterId(secret.getClusterId())
                .namespace(secret.getNamespace())
                .name(secret.getName())
                .type(secret.getSecretType())
                .dataCount(secret.getDataCount())
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(secret.getK8sCreatedAt() != null ? secret.getK8sCreatedAt() :
                          (secret.getCreatedAt() != null ? secret.getCreatedAt().toString() : null))
                .updatedAt(secret.getUpdatedAt() != null ? secret.getUpdatedAt().toString() : null)
                .isDeleted(secret.getIsDeleted() != null ? secret.getIsDeleted() : false)
                .labels(secret.getLabels())
                .annotations(secret.getAnnotations())
                .uid(secret.getUid())
                .build();
    }
}

