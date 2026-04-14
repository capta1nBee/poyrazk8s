package com.k8s.platform.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.SecurityRule;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.SecurityRuleRepository;
import com.k8s.platform.dto.request.CreateSecurityRuleRequest;
import com.k8s.platform.dto.request.UpdateSecurityRuleRequest;
import com.k8s.platform.dto.response.SecurityRuleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityRuleService {

    private final SecurityRuleRepository securityRuleRepository;
    private final ObjectMapper objectMapper;

    /**
     * Fetch all rules for a cluster (agent endpoint)
     */
    public List<SecurityRuleResponse> getClusterRules(String clusterUid) {
        log.info("Agent fetching rules for cluster: {}", clusterUid);

        List<SecurityRule> rules = securityRuleRepository.findByClusterUidAndEnabledTrue(clusterUid);
        return rules.stream()
                .map(rule -> {
                    SecurityRuleResponse response = SecurityRuleResponse.fromEntity(rule);
                    if (response.getCondition() != null) {
                        response.setCondition(normalizeCondition(response.getCondition()));
                    }
                    return response;
                })
                .collect(Collectors.toList());
    }

    /**
     * Normalizes rule conditions from various UI formats to the standard Agent format (all/any/op).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeCondition(Map<String, Object> condition) {
        if (condition == null) return null;

        java.util.Map<String, Object> normalized = new java.util.HashMap<>();

        // Handle logical grouping
        String type = null;
        java.util.List<Map<String, Object>> children = null;

        if (condition.containsKey("type")) { // React GroupCondition format
            type = condition.get("type").toString();
            children = (java.util.List<Map<String, Object>>) condition.get("children");
        } else if (condition.containsKey("logic")) { // Alternative format (logic/clauses)
            type = condition.get("logic").toString();
            children = (java.util.List<Map<String, Object>>) condition.get("clauses");
        }

        if (type != null && children != null) {
            java.util.List<Map<String, Object>> normalizedChildren = new java.util.ArrayList<>();
            for (Map<String, Object> child : children) {
                normalizedChildren.add(normalizeCondition(child));
            }
            normalized.put(type.toLowerCase(), normalizedChildren);
            return normalized;
        }

        // Handle leaf conditions (field comparisons)
        String field = (String) condition.get("field");
        if (field != null) {
            normalized.put("field", field);
            
            // Map 'operator' to 'op'
            String op = condition.containsKey("op") ? condition.get("op").toString() 
                       : (condition.containsKey("operator") ? condition.get("operator").toString() : null);
            
            if (op != null) {
                normalized.put("op", op);
                
                Object value = condition.get("value");
                // Fallback for list-based values in older formats
                if (value == null) {
                    if (condition.containsKey("in")) value = condition.get("in");
                    else if (condition.containsKey("not_in")) value = condition.get("not_in");
                }

                // Normalize value to list for IN/NOT_IN if it's a comma-separated string
                if (value instanceof String && (op.equals("in") || op.equals("not_in"))) {
                    String sValue = (String) value;
                    if (sValue.contains(",")) {
                        normalized.put("value", java.util.Arrays.stream(sValue.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.toList()));
                    } else {
                        normalized.put("value", java.util.List.of(sValue.trim()));
                    }
                } else {
                    normalized.put("value", value);
                }
            }
            return normalized;
        }

        return condition;
    }

    /**
     * Get active rules by cluster (for UI)
     */
    public Page<SecurityRuleResponse> getClusterRules(String clusterUid, Pageable pageable) {
        log.info("Fetching paged rules for cluster: {}", clusterUid);

        Page<SecurityRule> rules = securityRuleRepository.findByClusterUidAndIsActiveTrue(clusterUid, pageable);
        return rules.map(SecurityRuleResponse::fromEntity);
    }

    /**
     * Search rules with custom query
     */
    public Page<SecurityRuleResponse> searchRules(String clusterUid, String searchTerm, Pageable pageable) {
        log.info("Searching rules for cluster: {} with term: {}", clusterUid, searchTerm);

        Page<SecurityRule> rules = securityRuleRepository.searchRules(clusterUid, searchTerm, pageable);
        return rules.map(SecurityRuleResponse::fromEntity);
    }

    /**
     * Create new security rule
     */
    @Transactional
    public SecurityRuleResponse createRule(CreateSecurityRuleRequest request, User createdBy) {
        log.info("Creating security rule: {} for cluster: {}", request.getName(), request.getClusterUid());

        try {
            SecurityRule rule = SecurityRule.builder()
                    .clusterUid(request.getClusterUid())
                    .name(request.getName())
                    .description(request.getDescription())
                    .priority(request.getPriority())
                    .conditionJson(objectMapper.writeValueAsString(normalizeCondition(request.getCondition())))
                    .output(request.getOutput())
                    .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                    .tagsJson(request.getTags() != null ? objectMapper.writeValueAsString(request.getTags()) : null)
                    .ruleType(request.getRuleType() != null ? request.getRuleType() : "process")
                    .createdBy(createdBy.getId())
                    .isActive(true)
                    .build();

            SecurityRule savedRule = securityRuleRepository.save(rule);
            log.info("Security rule created successfully: {}", savedRule.getId());

            return SecurityRuleResponse.fromEntity(savedRule);
        } catch (Exception e) {
            log.error("Failed to create security rule", e);
            throw new RuntimeException("Failed to create security rule: " + e.getMessage());
        }
    }

    /**
     * Update security rule
     */
    @Transactional
    public SecurityRuleResponse updateRule(Long ruleId, UpdateSecurityRuleRequest request) {
        log.info("Updating security rule: {}", ruleId);

        SecurityRule rule = securityRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Security rule not found: " + ruleId));

        try {
            if (request.getDescription() != null) {
                rule.setDescription(request.getDescription());
            }
            if (request.getPriority() != null) {
                rule.setPriority(request.getPriority());
            }
            if (request.getCondition() != null) {
                rule.setConditionJson(objectMapper.writeValueAsString(normalizeCondition(request.getCondition())));
            }
            if (request.getOutput() != null) {
                rule.setOutput(request.getOutput());
            }
            if (request.getEnabled() != null) {
                rule.setEnabled(request.getEnabled());
            }
            if (request.getTags() != null) {
                rule.setTagsJson(objectMapper.writeValueAsString(request.getTags()));
            }

            SecurityRule updatedRule = securityRuleRepository.save(rule);
            log.info("Security rule updated successfully: {}", ruleId);

            return SecurityRuleResponse.fromEntity(updatedRule);
        } catch (Exception e) {
            log.error("Failed to update security rule", e);
            throw new RuntimeException("Failed to update security rule: " + e.getMessage());
        }
    }

    /**
     * Get rule by ID
     */
    public SecurityRuleResponse getRuleById(Long ruleId) {
        log.info("Fetching rule: {}", ruleId);

        SecurityRule rule = securityRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Security rule not found: " + ruleId));

        return SecurityRuleResponse.fromEntity(rule);
    }

    /**
     * Delete (deactivate) rule
     */
    @Transactional
    public void deleteRule(Long ruleId) {
        log.info("Deleting security rule: {}", ruleId);

        SecurityRule rule = securityRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Security rule not found: " + ruleId));

        rule.deactivate();
        securityRuleRepository.save(rule);
        log.info("Security rule deactivated: {}", ruleId);
    }

    /**
     * Toggle rule enabled/disabled status
     */
    @Transactional
    public SecurityRuleResponse toggleRule(Long ruleId) {
        log.info("Toggling security rule: {}", ruleId);

        SecurityRule rule = securityRuleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Security rule not found: " + ruleId));

        rule.toggleEnabled();
        SecurityRule updatedRule = securityRuleRepository.save(rule);

        log.info("Security rule toggled: {} - enabled: {}", ruleId, updatedRule.getEnabled());

        return SecurityRuleResponse.fromEntity(updatedRule);
    }

    /**
     * Get rules by priority
     */
    public List<SecurityRuleResponse> getRulesByPriority(String clusterUid, String priority) {
        log.info("Fetching {} priority rules for cluster: {}", priority, clusterUid);

        List<SecurityRule> rules = securityRuleRepository.findByClusterUidAndPriorityAndIsActiveTrue(clusterUid, priority);
        return rules.stream()
                .map(SecurityRuleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get count of active rules
     */
    public long getActiveRulesCount(String clusterUid) {
        return securityRuleRepository.countByClusterUidAndIsActiveTrue(clusterUid);
    }

    /**
     * Get count of enabled rules
     */
    public long getEnabledRulesCount(String clusterUid) {
        return securityRuleRepository.countByClusterUidAndEnabledTrueAndIsActiveTrue(clusterUid);
    }
}
