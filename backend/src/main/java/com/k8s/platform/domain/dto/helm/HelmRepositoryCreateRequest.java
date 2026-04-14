package com.k8s.platform.domain.dto.helm;

import lombok.Data;

@Data
public class HelmRepositoryCreateRequest {
    private String name;
    private String url;
    private boolean isPrivate;
    private String username;
    private String password;
}
