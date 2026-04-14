package com.k8s.platform.service.audit;

import com.k8s.platform.domain.entity.AuditLog;
import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.AuditLogRepository;
import com.k8s.platform.domain.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ClusterRepository clusterRepository;

    private String resolveClusterName(String clusterUid, String clusterName) {
        if (clusterName != null && !clusterName.isEmpty()) {
            return clusterName;
        }
        if (clusterUid != null && !clusterUid.isEmpty()) {
            return clusterRepository.findByUid(clusterUid)
                    .map(Cluster::getName)
                    .orElse(null);
        }
        return null;
    }

    @Transactional
    public void log(String action, String details) {
        log(action, details, null, null);
    }

    @Transactional
    public void log(String action, String details, String clusterUid, String clusterName) {
        String username = "system";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            username = auth.getName();
        }

        String effectiveClusterName = resolveClusterName(clusterUid, clusterName);

        AuditLog auditLog = AuditLog.builder()
                .username(username)
                .action(action)
                .details(details)
                .clusterUid(clusterUid)
                .clusterName(effectiveClusterName)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void log(String username, String action, String details) {
        log(username, action, details, null, null);
    }

    @Transactional
    public void log(String username, String action, String details, String clusterUid, String clusterName) {
        String effectiveClusterName = resolveClusterName(clusterUid, clusterName);

        AuditLog auditLog = AuditLog.builder()
                .username(username)
                .action(action)
                .details(details)
                .clusterUid(clusterUid)
                .clusterName(effectiveClusterName)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
    }

    public Page<AuditLog> getLogs(String username, String action, String details,
            String clusterUid, java.util.List<String> allowedClusterUids, Pageable pageable) {
        boolean filterByAllowed = allowedClusterUids != null && !allowedClusterUids.isEmpty();
        // Native query IN clause requires non-empty list; provide dummy when not filtering
        java.util.List<String> safeList = filterByAllowed ? allowedClusterUids : java.util.List.of("__NONE__");
        return auditLogRepository.findAllWithFilters(username, action, details, clusterUid, safeList,
                filterByAllowed, pageable);
    }
}
