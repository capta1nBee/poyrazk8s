package com.k8s.platform.dto.response;

import com.k8s.platform.domain.entity.SecurityRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SecurityRuleResponse {
    private Long id;
    private String clusterUid;
    private String name;
    private String description;
    private String priority;
    private Map<String, Object> condition;
    private String output;
    private Boolean enabled;
    private List<String> tags;
    private String ruleType;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SecurityRuleResponse fromEntity(SecurityRule rule) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> condition = null;
            if (rule.getConditionJson() != null) {
                condition = mapper.readValue(rule.getConditionJson(), Map.class);
            }

            List<String> tags = null;
            if (rule.getTagsJson() != null) {
                tags = mapper.readValue(rule.getTagsJson(), List.class);
            }

            return SecurityRuleResponse.builder()
                    .id(rule.getId())
                    .clusterUid(rule.getClusterUid())
                    .name(rule.getName())
                    .description(rule.getDescription())
                    .priority(rule.getPriority())
                    .condition(condition)
                    .output(rule.getOutput())
                    .enabled(rule.getEnabled())
                    .tags(tags)
                    .ruleType(rule.getRuleType())
                    .createdBy(rule.getCreatedByUser() != null ? rule.getCreatedByUser().getUsername() : null)
                    .createdAt(rule.getCreatedAt())
                    .updatedAt(rule.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            // Log error but return basic response
            return SecurityRuleResponse.builder()
                    .id(rule.getId())
                    .clusterUid(rule.getClusterUid())
                    .name(rule.getName())
                    .description(rule.getDescription())
                    .priority(rule.getPriority())
                    .enabled(rule.getEnabled())
                    .ruleType(rule.getRuleType())
                    .createdAt(rule.getCreatedAt())
                    .updatedAt(rule.getUpdatedAt())
                    .build();
        }
    }
}
