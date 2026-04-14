package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.K8sNamespace;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String name;
    private String status; // Active | Terminating
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional fields for UI
    private String uid;
    private String labels;
    private String annotations;

    public static NamespaceResponseDTO fromEntity(K8sNamespace namespace) {
        return NamespaceResponseDTO.builder()
                .id(namespace.getId())
                .kind("Namespace")
                .clusterId(namespace.getClusterId())
                .name(namespace.getName())
                .status(namespace.getStatus())
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(namespace.getK8sCreatedAt() != null ? namespace.getK8sCreatedAt() :
                          (namespace.getCreatedAt() != null ? namespace.getCreatedAt().toString() : null))
                .updatedAt(namespace.getUpdatedAt() != null ? namespace.getUpdatedAt().toString() : null)
                .isDeleted(namespace.getIsDeleted() != null ? namespace.getIsDeleted() : false)
                .uid(namespace.getUid())
                .labels(namespace.getLabels())
                .annotations(namespace.getAnnotations())
                .build();
    }
}
