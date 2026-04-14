package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.Ingress;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngressResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private String ingressClass;
    private String hosts; // Extracted from rules
    private String paths; // Extracted from rules
    private Boolean tlsEnabled;
    private String address; // Load balancer address
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional UI fields
    private String ingressClassName;
    private String rules; // JSON
    private String tls; // JSON
    private String status; // JSON
    private String annotations;
    private String labels;
    private String uid;
    private String resourceVersion;

    public static IngressResponseDTO fromEntity(Ingress ingress) {
        return IngressResponseDTO.builder()
                .id(ingress.getId())
                .kind("Ingress")
                .clusterId(ingress.getClusterId())
                .namespace(ingress.getNamespace())
                .name(ingress.getName())
                .ingressClass(ingress.getIngressClassName())
                .hosts(extractHosts(ingress.getRules()))
                .paths(extractPaths(ingress.getRules()))
                .tlsEnabled(ingress.getTls() != null && !ingress.getTls().isEmpty() && !ingress.getTls().equals("[]"))
                .address(extractAddress(ingress.getStatus()))
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(ingress.getK8sCreatedAt() != null ? ingress.getK8sCreatedAt() :
                          (ingress.getCreatedAt() != null ? ingress.getCreatedAt().toString() : null))
                .updatedAt(ingress.getUpdatedAt() != null ? ingress.getUpdatedAt().toString() : null)
                .isDeleted(ingress.getIsDeleted() != null ? ingress.getIsDeleted() : false)
                .ingressClassName(ingress.getIngressClassName())
                .rules(ingress.getRules())
                .tls(ingress.getTls())
                .status(ingress.getStatus())
                .annotations(ingress.getAnnotations())
                .labels(ingress.getLabels())
                .uid(ingress.getUid())
                .resourceVersion(ingress.getResourceVersion())
                .build();
    }

    private static String extractHosts(String rulesJson) {
        if (rulesJson == null || rulesJson.isEmpty() || rulesJson.equals("[]")) {
            return null;
        }
        try {
            // Simple extraction - in production use proper JSON parsing
            if (rulesJson.contains("\"host\"")) {
                return rulesJson.replaceAll(".*\"host\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            }
        } catch (Exception e) {
            // Return null if parsing fails
        }
        return null;
    }

    private static String extractPaths(String rulesJson) {
        if (rulesJson == null || rulesJson.isEmpty() || rulesJson.equals("[]")) {
            return null;
        }
        try {
            if (rulesJson.contains("\"path\"")) {
                return rulesJson.replaceAll(".*\"path\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            }
        } catch (Exception e) {
            // Return null if parsing fails
        }
        return "/";
    }

    private static String extractAddress(String statusJson) {
        if (statusJson == null || statusJson.isEmpty()) {
            return null;
        }
        try {
            if (statusJson.contains("\"ip\"")) {
                return statusJson.replaceAll(".*\"ip\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            } else if (statusJson.contains("\"hostname\"")) {
                return statusJson.replaceAll(".*\"hostname\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            }
        } catch (Exception e) {
            // Return null if parsing fails
        }
        return null;
    }
}
