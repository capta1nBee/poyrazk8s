package com.k8s.platform.domain.dto.appcreator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AppCreatorK8sResourceDto {
    private String kind;
    private String name;
    private String namespace;
    private boolean exists;
}

