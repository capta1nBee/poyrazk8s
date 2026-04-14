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
public class PolicyConflictDTO {

    // The existing policy that conflicts
    private String existingPolicyName;
    private String existingPolicyNamespace;
    private String existingPolicyType;

    // Type of conflict
    private ConflictType conflictType;

    // Severity: warning, error
    private String severity;

    // Human-readable description of the conflict
    private String description;

    // Details about what specifically conflicts
    private List<ConflictDetail> details;

    // Whether this conflict can be automatically resolved
    private boolean autoResolvable;

    // Suggested resolution
    private String suggestedResolution;

    public enum ConflictType {
        DUPLICATE_SELECTOR,      // Same pod selector targets same pods
        OVERLAPPING_RULES,       // Rules that overlap with existing rules
        CONFLICTING_PORTS,       // Same port with different allow/deny
        NAMESPACE_ISOLATION,     // Conflicts with namespace isolation policies
        DEFAULT_DENY_OVERRIDE,   // Overrides a default deny policy
        REDUNDANT_RULE          // Rule already covered by existing policy
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictDetail {
        private String field;            // Which field conflicts
        private String existingValue;    // Value in existing policy
        private String newValue;         // Value in new policy
        private String explanation;      // Why this is a conflict
    }
}
