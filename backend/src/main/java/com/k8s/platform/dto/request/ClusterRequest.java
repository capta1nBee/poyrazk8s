package com.k8s.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterRequest {

    @NotBlank(message = "Cluster name is required")
    private String name;

    private String apiServer;

    @NotBlank(message = "Auth type is required")
    private String authType; // TOKEN | CERT | OIDC

    private String kubeconfig; // Base64 encoded kubeconfig

    private Boolean vulnScanEnabled;

    private String privateRegistryUser;

    private String privateRegistryPassword;
}
