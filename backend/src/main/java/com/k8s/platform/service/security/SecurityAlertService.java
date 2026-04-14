package com.k8s.platform.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.SecurityAlert;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.SecurityAlertRepository;
import com.k8s.platform.dto.request.AcknowledgeAlertRequest;
import com.k8s.platform.dto.request.ResolveAlertRequest;
import com.k8s.platform.dto.response.SecurityAlertResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAlertService {

    private final SecurityAlertRepository securityAlertRepository;
    private final ObjectMapper objectMapper;

    /**
     * Receive alert from runtime security agent
     */
    @Transactional
    public SecurityAlertResponse receiveAlert(String clusterUid, Map<String, Object> alertData) {
        log.info("Receiving security alert for cluster: {}", clusterUid);

        try {
            String fingerprint = (String) alertData.get("fingerprint");
            
            // Check for duplicate alert
            if (fingerprint != null) {
                Optional<SecurityAlert> existingAlert = securityAlertRepository.findByFingerprint(fingerprint);
                if (existingAlert.isPresent()) {
                    log.info("Duplicate alert detected, skipping: {}", fingerprint);
                    return SecurityAlertResponse.fromEntity(existingAlert.get());
                }
            }

            SecurityAlert alert = SecurityAlert.builder()
                    .clusterUid(clusterUid)
                    .eventType((String) alertData.get("event_type"))
                    .priority((String) alertData.get("priority"))
                    .ruleName((String) alertData.get("rule_name"))
                    .ruleDescription((String) alertData.getOrDefault("rule_description", ""))
                    .output((String) alertData.get("output"))
                    .namespaceName((String) alertData.get("namespace"))
                    .podName((String) alertData.get("pod_name"))
                    .containerId((String) alertData.get("container_id"))
                    .eventDataJson(objectMapper.writeValueAsString(alertData.get("event_data")))
                    .fingerprint(fingerprint)
                    .tagsJson(objectMapper.writeValueAsString(alertData.getOrDefault("tags", List.of())))
                    .isAcknowledged(false)
                    .resolved(false)
                    .build();

            SecurityAlert savedAlert = securityAlertRepository.save(alert);
            log.info("Security alert received and stored: {}", savedAlert.getId());

            return SecurityAlertResponse.fromEntity(savedAlert);
        } catch (Exception e) {
            log.error("Failed to receive security alert", e);
            throw new RuntimeException("Failed to receive security alert: " + e.getMessage());
        }
    }

    /**
     * Get pending alerts for a cluster
     */
    public List<SecurityAlertResponse> getPendingAlerts(String clusterUid) {
        log.info("Fetching pending alerts for cluster: {}", clusterUid);

        List<SecurityAlert> alerts = securityAlertRepository
                .findByClusterUidAndIsAcknowledgedFalseAndResolvedFalse(clusterUid);

        return alerts.stream()
                .map(SecurityAlertResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get alerts with pagination
     */
    public Page<SecurityAlertResponse> getAlerts(String clusterUid, Pageable pageable) {
        log.info("Fetching alerts for cluster: {} with pagination", clusterUid);

        Page<SecurityAlert> alerts = securityAlertRepository.findByClusterUid(clusterUid, pageable);
        return alerts.map(SecurityAlertResponse::fromEntity);
    }

    /**
     * Get recent alerts
     */
    public List<SecurityAlertResponse> getRecentAlerts(String clusterUid, int limit) {
        log.info("Fetching recent {} alerts for cluster: {}", limit, clusterUid);

        List<SecurityAlert> alerts = securityAlertRepository.findByClusterUidOrderByCreatedAtDesc(clusterUid);
        return alerts.stream()
                .limit(limit)
                .map(SecurityAlertResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Search alerts with advanced filter
     */
    public Page<SecurityAlertResponse> searchAlerts(String clusterUid, String priority, String namespace,
                                                   String podName, Boolean acknowledged, Boolean resolved,
                                                   LocalDateTime startDate, Pageable pageable) {
        log.info("Searching alerts for cluster: {} with filters", clusterUid);

        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(7); // Default to last 7 days
        }

        Page<SecurityAlert> alerts = securityAlertRepository.searchAlerts(
                clusterUid, priority, namespace, podName, acknowledged, resolved, startDate, pageable);

        return alerts.map(SecurityAlertResponse::fromEntity);
    }

    /**
     * Get alert by ID
     */
    public SecurityAlertResponse getAlertById(Long alertId) {
        log.info("Fetching alert: {}", alertId);

        SecurityAlert alert = securityAlertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Security alert not found: " + alertId));

        return SecurityAlertResponse.fromEntity(alert);
    }

    /**
     * Acknowledge alert
     */
    @Transactional
    public SecurityAlertResponse acknowledgeAlert(Long alertId, AcknowledgeAlertRequest request, User acknowledgedBy) {
        log.info("Acknowledging alert: {} by user: {}", alertId, acknowledgedBy.getUsername());

        SecurityAlert alert = securityAlertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Security alert not found: " + alertId));

        alert.acknowledge(acknowledgedBy.getId(), request.getNote());
        SecurityAlert updatedAlert = securityAlertRepository.save(alert);

        log.info("Alert acknowledged: {}", alertId);

        return SecurityAlertResponse.fromEntity(updatedAlert);
    }

    /**
     * Resolve alert
     */
    @Transactional
    public SecurityAlertResponse resolveAlert(Long alertId, ResolveAlertRequest request, User resolvedBy) {
        log.info("Resolving alert: {} by user: {}", alertId, resolvedBy.getUsername());

        SecurityAlert alert = securityAlertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Security alert not found: " + alertId));

        alert.resolve(resolvedBy.getId(), request.getNote());
        SecurityAlert updatedAlert = securityAlertRepository.save(alert);

        log.info("Alert resolved: {}", alertId);

        return SecurityAlertResponse.fromEntity(updatedAlert);
    }

    /**
     * Get count of pending alerts
     */
    public long getPendingAlertsCount(String clusterUid) {
        return securityAlertRepository.countByClusterUidAndIsAcknowledgedFalseAndResolvedFalse(clusterUid);
    }

    /**
     * Get count of critical alerts
     */
    public long getCriticalAlertsCount(String clusterUid) {
        return securityAlertRepository.countByClusterUidAndPriority(clusterUid, "CRITICAL");
    }

    /**
     * Get alerts by pod
     */
    public Page<SecurityAlertResponse> getAlertsByPod(String clusterUid, String podName, Pageable pageable) {
        log.info("Fetching alerts for pod: {} in cluster: {}", podName, clusterUid);

        Page<SecurityAlert> alerts = securityAlertRepository.findByClusterUidAndPodName(clusterUid, podName, pageable);
        return alerts.map(SecurityAlertResponse::fromEntity);
    }

    /**
     * Get alerts by namespace
     */
    public Page<SecurityAlertResponse> getAlertsByNamespace(String clusterUid, String namespace, Pageable pageable) {
        log.info("Fetching alerts for namespace: {} in cluster: {}", namespace, clusterUid);

        Page<SecurityAlert> alerts = securityAlertRepository.findByClusterUidAndNamespaceName(clusterUid, namespace, pageable);
        return alerts.map(SecurityAlertResponse::fromEntity);
    }
}
