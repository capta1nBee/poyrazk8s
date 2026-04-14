package com.k8s.platform.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecretResponseDTO {

    private String id;
    private String name;
    private String namespace;
    private String type; // Opaque, kubernetes.io/service-account-token, etc.
    private List<String> keys; // Secret keys
    private String age;
    private String yaml;

}
