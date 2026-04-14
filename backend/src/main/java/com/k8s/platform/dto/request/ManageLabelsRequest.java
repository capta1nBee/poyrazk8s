package com.k8s.platform.dto.request;

import lombok.Data;

import java.util.Map;

@Data
public class ManageLabelsRequest {
    private Map<String, String> labels;
    private Map<String, String> annotations;
}

