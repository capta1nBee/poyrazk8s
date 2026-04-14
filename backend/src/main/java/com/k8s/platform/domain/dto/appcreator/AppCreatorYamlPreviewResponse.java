package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AppCreatorYamlPreviewResponse {
    private Map<String, String> files; // filename -> yaml content
    private Integer resourceCount;
}

