package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterValidationResponse {

    private boolean valid;
    private String message;
    
    // Cluster info extracted from kubeconfig
    private String clusterName;
    private String apiServer;
    private String kubernetesVersion;
    private String clusterUid;
    private Integer nodeCount;
    
    // Error details if validation fails
    private String errorType;
    private String errorDetails;

    public static ClusterValidationResponse success(String clusterName, String apiServer, 
            String version, String clusterUid, Integer nodeCount) {
        return ClusterValidationResponse.builder()
                .valid(true)
                .message("Kubeconfig is valid and cluster is reachable")
                .clusterName(clusterName)
                .apiServer(apiServer)
                .kubernetesVersion(version)
                .clusterUid(clusterUid)
                .nodeCount(nodeCount)
                .build();
    }

    public static ClusterValidationResponse failure(String errorType, String message, String details) {
        return ClusterValidationResponse.builder()
                .valid(false)
                .message(message)
                .errorType(errorType)
                .errorDetails(details)
                .build();
    }
}

