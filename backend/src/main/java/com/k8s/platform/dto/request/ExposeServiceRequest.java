package com.k8s.platform.dto.request;

import lombok.Data;

@Data
public class ExposeServiceRequest {
    private String type; // NodePort | LoadBalancer
    private Integer port;
    private Integer targetPort;
}

