package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.EndpointSlice;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointSliceResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private String serviceName;
    private Integer addressCount;
    private Integer portCount;
    private String createdAt;
    private String updatedAt;
    // Additional UI fields
    private String addressType;
    private String endpoints; // JSON
    private String ports; // JSON
    private String labels;
    private String annotations;
    private String uid;

    public static EndpointSliceResponseDTO fromEntity(EndpointSlice endpointSlice) {
        return EndpointSliceResponseDTO.builder()
                .id(endpointSlice.getId())
                .kind("EndpointSlice")
                .clusterId(endpointSlice.getClusterId())
                .namespace(endpointSlice.getNamespace())
                .name(endpointSlice.getName())
                .serviceName(extractServiceName(endpointSlice.getLabels()))
                .addressCount(countAddresses(endpointSlice.getEndpoints()))
                .portCount(countPorts(endpointSlice.getPorts()))
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .createdAt(endpointSlice.getK8sCreatedAt() != null ? endpointSlice.getK8sCreatedAt() :
                          (endpointSlice.getCreatedAt() != null ? endpointSlice.getCreatedAt().toString() : null))
                .updatedAt(endpointSlice.getUpdatedAt() != null ? endpointSlice.getUpdatedAt().toString() : null)
                .addressType(endpointSlice.getAddressType())
                .endpoints(endpointSlice.getEndpoints())
                .ports(endpointSlice.getPorts())
                .labels(endpointSlice.getLabels())
                .annotations(endpointSlice.getAnnotations())
                .uid(endpointSlice.getUid())
                .build();
    }

    private static String extractServiceName(String labelsJson) {
        if (labelsJson == null || labelsJson.isEmpty()) {
            return null;
        }
        try {
            // Extract kubernetes.io/service-name label
            if (labelsJson.contains("\"kubernetes.io/service-name\"")) {
                return labelsJson.replaceAll(".*\"kubernetes\\.io/service-name\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            }
        } catch (Exception e) {
            // Return null if parsing fails
        }
        return null;
    }

    private static Integer countAddresses(String endpointsJson) {
        if (endpointsJson == null || endpointsJson.isEmpty() || endpointsJson.equals("[]")) {
            return 0;
        }
        try {
            // Count addresses in endpoints array
            int count = 0;
            String[] parts = endpointsJson.split("\"addresses\"");
            for (int i = 1; i < parts.length; i++) {
                String addressPart = parts[i].split("]")[0];
                count += addressPart.split("\"").length / 2;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private static Integer countPorts(String portsJson) {
        if (portsJson == null || portsJson.isEmpty() || portsJson.equals("[]")) {
            return 0;
        }
        try {
            // Simple count of port objects
            int count = portsJson.split("\"port\"").length - 1;
            return count > 0 ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}

