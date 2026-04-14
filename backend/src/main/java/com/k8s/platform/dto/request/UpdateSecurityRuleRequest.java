package com.k8s.platform.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateSecurityRuleRequest {
    private String description;
    private String priority; // CRITICAL, HIGH, MEDIUM, LOW
    private Map<String, Object> condition; // JSON condition object
    private String output; // Output message template
    private Boolean enabled;
    private List<String> tags;
}
