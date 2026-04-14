package com.k8s.platform.dto.request;

import lombok.Data;

@Data
public class PortForwardRequest {
    private Integer localPort;
    private Integer podPort;
}

