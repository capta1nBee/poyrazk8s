package com.k8s.platform.domain.dto.helm;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeployRequest {
    private String releaseName;
    private String deployName;
    private String namespace;
    private String chartName;
    private String chartVersion;
    private String repoUrl;                       // Added for automatic repo management
    private java.util.UUID repoId;                // Added for saved repo selection
    private Map<String, Object> customValues;     // Retained for backward compat
    private String customValuesYaml;              // New raw YAML string support for ArtifactHub
}
