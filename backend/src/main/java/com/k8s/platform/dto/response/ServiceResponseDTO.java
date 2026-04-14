package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.Service;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private String type; // ClusterIP | NodePort | LoadBalancer
    private String clusterIP;
    private String externalIP;
    private String ports; // JSON
    private String selector; // JSON
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional UI fields
    private String loadBalancerIP;
    private String externalName;
    private String sessionAffinity;
    private String loadBalancerSourceRanges;
    private String ipFamilies;
    private String ipFamilyPolicy;
    private String labels;
    private String annotations;
    private String uid;
    private String resourceVersion;

    public static ServiceResponseDTO fromEntity(Service service) {
        return ServiceResponseDTO.builder()
                .id(service.getId())
                .kind("Service")
                .clusterId(service.getClusterId())
                .namespace(service.getNamespace())
                .name(service.getName())
                .type(service.getServiceType())
                .clusterIP(service.getClusterIP())
                .externalIP(service.getExternalIP())
                .ports(service.getPorts())
                .selector(service.getSelector())
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(service.getK8sCreatedAt() != null ? service.getK8sCreatedAt() :
                          (service.getCreatedAt() != null ? service.getCreatedAt().toString() : null))
                .updatedAt(service.getUpdatedAt() != null ? service.getUpdatedAt().toString() : null)
                .isDeleted(service.getIsDeleted() != null ? service.getIsDeleted() : false)
                .loadBalancerIP(service.getLoadBalancerIP())
                .externalName(service.getExternalName())
                .sessionAffinity(service.getSessionAffinity())
                .loadBalancerSourceRanges(service.getLoadBalancerSourceRanges())
                .ipFamilies(service.getIpFamilies())
                .ipFamilyPolicy(service.getIpFamilyPolicy())
                .labels(service.getLabels())
                .annotations(service.getAnnotations())
                .uid(service.getUid())
                .resourceVersion(service.getResourceVersion())
                .build();
    }
}
