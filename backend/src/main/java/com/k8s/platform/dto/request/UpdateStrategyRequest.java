package com.k8s.platform.dto.request;

import lombok.Data;

@Data
public class UpdateStrategyRequest {
    private String strategy; // RollingUpdate | OnDelete
}

