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
public class ConfigMapResponseDTO {

    private String id;
    private String name;
    private String namespace;
    private List<String> keys; // ConfigMap keys
    private String age;
    private String yaml;

}
