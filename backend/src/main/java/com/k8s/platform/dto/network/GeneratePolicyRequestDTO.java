package com.k8s.platform.dto.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePolicyRequestDTO {

    // Target namespace for the policy
    private String namespace;

    // Policy name (optional, will be auto-generated if not provided)
    private String name;

    // Policy type: ingress or egress
    private String policyType;

    // Pod selector - which pods this policy applies to
    private Map<String, String> podSelector;

    // Selected rules to include in the policy
    private List<NetworkPolicyRuleDTO> selectedRules;

    // Optional description
    private String description;

    // Whether to auto-apply after generation
    private boolean autoApply;
}
