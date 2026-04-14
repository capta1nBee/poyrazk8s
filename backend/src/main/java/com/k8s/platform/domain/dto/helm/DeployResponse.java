package com.k8s.platform.domain.dto.helm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeployResponse {
    private boolean success;
    private String releaseName;
    private String deployName;
    private String namespace;
    private String chartName;
    private String chartVersion;
    private String status;
    private String logs;
}
