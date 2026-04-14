package com.k8s.platform.dto.request;

import lombok.Data;

@Data
public class RollbackRequest {
    private Integer revision;
}

