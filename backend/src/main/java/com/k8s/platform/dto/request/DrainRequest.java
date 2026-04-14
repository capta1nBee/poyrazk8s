package com.k8s.platform.dto.request;

import lombok.Data;

@Data
public class DrainRequest {
    private Boolean force = false;
    private Boolean deletePods = true;
    private Boolean ignoreDaemonSets = true;
    private Integer timeout = 300;
}

