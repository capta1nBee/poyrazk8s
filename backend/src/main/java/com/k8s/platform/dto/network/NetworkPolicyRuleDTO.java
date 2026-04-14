package com.k8s.platform.dto.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkPolicyRuleDTO {

    // Source information
    private String sourceNamespace;
    private String sourcePodName;
    private Map<String, String> sourcePodLabels;
    private String sourceIp;
    private Integer sourcePort;
    private String sourceKind;

    // Destination information
    private String destinationNamespace;
    private String destinationPodName;
    private Map<String, String> destinationPodLabels;
    private String destinationIp;
    private Integer destinationPort;
    private String destinationKind;

    // Service information (if destination is a service)
    private String serviceName;
    private String serviceNamespace;
    private Map<String, String> serviceSelector;
    private String backendPodName;
    private String backendPodNamespace;
    private Map<String, String> backendPodLabels;
    private Integer backendPodPort;

    // Network information
    private String protocol;
    private String flowType;

    // Statistics
    private Long flowCount;
    private Long totalBytes;

    // For UI selection
    private boolean selected;
    private String ruleId; // Unique identifier for the rule
}
