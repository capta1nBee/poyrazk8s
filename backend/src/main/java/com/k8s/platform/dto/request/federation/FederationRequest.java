package com.k8s.platform.dto.request.federation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FederationRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Master cluster ID is required")
    private Long masterClusterId;

    @NotEmpty(message = "At least one member cluster is required")
    private List<Long> memberClusterIds;

    @NotEmpty(message = "At least one resource is required")
    private List<FederationResourceDto> resources;

    @Data
    public static class FederationResourceDto {
        @NotBlank(message = "Resource kind is required")
        private String kind;

        @NotBlank(message = "Namespace is required")
        private String namespace;

        @NotBlank(message = "Resource name is required")
        private String name;
    }
}
