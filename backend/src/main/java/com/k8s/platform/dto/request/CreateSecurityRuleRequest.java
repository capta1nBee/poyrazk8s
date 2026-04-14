package com.k8s.platform.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateSecurityRuleRequest {
    private String clusterUid;
    private String name;
    private String description;
    private String priority; // CRITICAL, HIGH, MEDIUM, LOW
    private Map<String, Object> condition; // JSON condition object
    private String output; // Output message template
    private Boolean enabled = true;
    private List<String> tags;
    private String ruleType; // process, network, file, file_access, etc.
}
