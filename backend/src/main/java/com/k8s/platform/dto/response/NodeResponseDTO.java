package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.K8sNode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String name;
    private String status; // Ready | NotReady
    private Boolean unschedulable;
    private String roles; // master | worker
    private String cpuCapacity;
    private String memoryCapacity;
    private String allocatableCpu;
    private String allocatableMemory;
    private String kubeletVersion;
    private String os;
    private String kernel;
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional fields for UI
    private String nodeIP;
    private String capacity; // JSON
    private String allocatable; // JSON
    private String ownerReferences;
    private String labels;
    private String annotations;

    public static NodeResponseDTO fromEntity(K8sNode node) {
        return NodeResponseDTO.builder()
                .id(node.getId())
                .kind("Node")
                .clusterId(node.getClusterId())
                .name(node.getName())
                .status(node.getStatus())
                .unschedulable(node.getUnschedulable())
                .roles(node.getRoles())
                .cpuCapacity(node.getCpuCapacity())
                .memoryCapacity(node.getMemoryCapacity())
                .allocatableCpu(node.getAllocatableCpu())
                .allocatableMemory(node.getAllocatableMemory())
                .kubeletVersion(node.getKubeletVersion())
                .os(node.getOs())
                .kernel(node.getKernel())
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(node.getK8sCreatedAt() != null ? node.getK8sCreatedAt()
                        : (node.getCreatedAt() != null ? node.getCreatedAt().toString() : null))
                .updatedAt(node.getUpdatedAt() != null ? node.getUpdatedAt().toString() : null)
                .isDeleted(node.getIsDeleted() != null ? node.getIsDeleted() : false)
                .nodeIP(node.getNodeIP())
                .capacity(node.getCapacity())
                .allocatable(node.getAllocatable())
                .ownerReferences(node.getOwnerRefs())
                .labels(node.getLabels())
                .annotations(node.getAnnotations())
                .build();
    }
}
