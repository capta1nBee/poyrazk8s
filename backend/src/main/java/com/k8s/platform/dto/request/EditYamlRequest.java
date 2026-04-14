package com.k8s.platform.dto.request;

import lombok.Data;

@Data
public class EditYamlRequest {
    private String yaml;
    private Boolean dryRun = false;
}

