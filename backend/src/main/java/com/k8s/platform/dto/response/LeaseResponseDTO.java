package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.Lease;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaseResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private String holderIdentity;
    private String renewTime;
    private String createdAt;
    private String updatedAt;
    // Additional UI fields
    private Integer leaseDurationSeconds;
    private String acquireTime;
    private String labels;
    private String annotations;
    private String uid;

    public static LeaseResponseDTO fromEntity(Lease lease) {
        return LeaseResponseDTO.builder()
                .id(lease.getId())
                .kind("Lease")
                .clusterId(lease.getClusterId())
                .namespace(lease.getNamespace())
                .name(lease.getName())
                .holderIdentity(lease.getHolderIdentity())
                .renewTime(lease.getRenewTime() != null ? lease.getRenewTime().toString() : null)
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .createdAt(lease.getK8sCreatedAt() != null ? lease.getK8sCreatedAt() :
                          (lease.getCreatedAt() != null ? lease.getCreatedAt().toString() : null))
                .updatedAt(lease.getUpdatedAt() != null ? lease.getUpdatedAt().toString() : null)
                .leaseDurationSeconds(lease.getLeaseDurationSeconds())
                .acquireTime(lease.getAcquireTime() != null ? lease.getAcquireTime().toString() : null)
                .labels(lease.getLabels())
                .annotations(lease.getAnnotations())
                .uid(lease.getUid())
                .build();
    }
}

