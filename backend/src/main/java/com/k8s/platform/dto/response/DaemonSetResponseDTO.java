package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.DaemonSet;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DaemonSetResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private Integer desiredPods;
    private Integer currentPods;
    private Integer readyPods;
    private String nodeSelector; // JSON
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional UI fields
    private Integer numberAvailable;
    private String labels;
    private String annotations;

    public static DaemonSetResponseDTO fromEntity(DaemonSet daemonSet) {
        return DaemonSetResponseDTO.builder()
                .id(daemonSet.getId())
                .kind("DaemonSet")
                .clusterId(daemonSet.getClusterId())
                .namespace(daemonSet.getNamespace())
                .name(daemonSet.getName())
                .desiredPods(daemonSet.getDesiredNumberScheduled())
                .currentPods(daemonSet.getCurrentNumberScheduled())
                .readyPods(daemonSet.getNumberReady())
                .nodeSelector(daemonSet.getNodeSelector())
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(daemonSet.getK8sCreatedAt() != null ? daemonSet.getK8sCreatedAt() :
                          (daemonSet.getCreatedAt() != null ? daemonSet.getCreatedAt().toString() : null))
                .updatedAt(daemonSet.getUpdatedAt() != null ? daemonSet.getUpdatedAt().toString() : null)
                .isDeleted(daemonSet.getIsDeleted() != null ? daemonSet.getIsDeleted() : false)
                .numberAvailable(daemonSet.getNumberAvailable())
                .labels(daemonSet.getLabels())
                .annotations(daemonSet.getAnnotations())
                .build();
    }
}

