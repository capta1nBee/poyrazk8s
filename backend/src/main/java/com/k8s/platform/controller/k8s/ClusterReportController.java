package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.K8sNode;
import com.k8s.platform.domain.repository.AuditLogRepository;
import com.k8s.platform.domain.repository.BackupRepository;
import com.k8s.platform.domain.repository.ExecSessionRepository;
import com.k8s.platform.domain.repository.k8s.*;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/k8s/{clusterUid}/reports")
@RequiredArgsConstructor
@Slf4j
public class ClusterReportController {

    private final ClusterRepository clusterRepository;
    private final PodRepository podRepository;
    private final DeploymentRepository deploymentRepository;
    private final K8sNodeRepository nodeRepository;
    private final K8sNamespaceRepository namespaceRepository;
    private final ServiceRepository serviceRepository;
    private final BackupRepository backupRepository;
    private final AuditLogRepository auditLogRepository;
    private final ExecSessionRepository execSessionRepository;
    private final ResourceAuthorizationHelper authHelper;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getClusterReport(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "reports");

        var cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));
        Long clusterId = cluster.getId();
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        // ── Resource overview ─────────────────────────────────────────────────
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("nodes",       nodeRepository.countByClusterIdAndIsDeletedFalse(clusterId));
        resources.put("namespaces",  namespaceRepository.countByClusterIdAndIsDeletedFalse(clusterId));
        resources.put("pods",        podRepository.countByClusterIdAndIsDeletedFalse(clusterId));
        resources.put("deployments", deploymentRepository.countByClusterIdAndIsDeletedFalse(clusterId));
        resources.put("services",    serviceRepository.countByClusterIdAndIsDeletedFalse(clusterId));

        // ── Pod health breakdown ───────────────────────────────────────────────
        long runningPods   = podRepository.findByClusterIdAndPhaseAndIsDeletedFalse(clusterId, "Running").size();
        long pendingPods   = podRepository.findByClusterIdAndPhaseAndIsDeletedFalse(clusterId, "Pending").size();
        long failedPods    = podRepository.findByClusterIdAndPhaseAndIsDeletedFalse(clusterId, "Failed").size();
        // "Succeeded" = completed Jobs/batch pods — must be counted explicitly
        // so they are NOT lumped into the "unknown" bucket
        long succeededPods = podRepository.findByClusterIdAndPhaseAndIsDeletedFalse(clusterId, "Succeeded").size();
        long unknownPods   = ((Number) resources.get("pods")).longValue()
                             - runningPods - pendingPods - failedPods - succeededPods;
        if (unknownPods < 0) unknownPods = 0;
        Map<String, Object> podHealth = new LinkedHashMap<>();
        podHealth.put("running",   runningPods);
        podHealth.put("pending",   pendingPods);
        podHealth.put("failed",    failedPods);
        podHealth.put("completed", succeededPods);   // Succeeded / Completed
        podHealth.put("unknown",   unknownPods);

        // ── Node status ────────────────────────────────────────────────────────
        List<K8sNode> nodes = nodeRepository.findByClusterIdAndIsDeletedFalse(clusterId);
        long readyNodes    = nodes.stream().filter(n -> "Ready".equalsIgnoreCase(n.getStatus())).count();
        long notReadyNodes = nodes.size() - readyNodes;
        Map<String, Object> nodeHealth = new LinkedHashMap<>();
        nodeHealth.put("ready",    readyNodes);
        nodeHealth.put("notReady", notReadyNodes);

        // ── Backup status ─────────────────────────────────────────────────────
        var backups = backupRepository.findByClusterIdOrderByCreatedAtDesc(clusterId);
        long totalBackups     = backups.size();
        long completedBackups = backups.stream().filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus())).count();
        long failedBackups    = backups.stream().filter(b -> "FAILED".equalsIgnoreCase(b.getStatus())).count();
        String lastBackupTime = backups.isEmpty() ? null
                : backups.get(0).getCreatedAt() != null ? backups.get(0).getCreatedAt().toString() : null;
        String lastBackupStatus = backups.isEmpty() ? "NONE" : backups.get(0).getStatus();
        Map<String, Object> backupStatus = new LinkedHashMap<>();
        backupStatus.put("total",     totalBackups);
        backupStatus.put("completed", completedBackups);
        backupStatus.put("failed",    failedBackups);
        backupStatus.put("lastTime",  lastBackupTime);
        backupStatus.put("lastStatus",lastBackupStatus);
        backupStatus.put("successRate", totalBackups > 0 ? Math.round((completedBackups * 100.0) / totalBackups) : 0);

        // ── Audit activity (last 30 days) ─────────────────────────────────────
        long totalAuditEvents = auditLogRepository.countByClusterUid(clusterUid);
        List<Object[]> topUsersRaw    = auditLogRepository.findTopUsersByCluster(clusterUid, thirtyDaysAgo);
        List<Object[]> topActionsRaw  = auditLogRepository.findTopActionsByCluster(clusterUid, thirtyDaysAgo);
        List<Map<String, Object>> topUsers = topUsersRaw.stream().limit(5)
                .map(r -> { Map<String,Object> m = new LinkedHashMap<>(); m.put("username", r[0]); m.put("count", r[1]); return m; })
                .collect(Collectors.toList());
        List<Map<String, Object>> topActions = topActionsRaw.stream().limit(5)
                .map(r -> { Map<String,Object> m = new LinkedHashMap<>(); m.put("action", r[0]); m.put("count", r[1]); return m; })
                .collect(Collectors.toList());
        List<Map<String, Object>> recentLogs = auditLogRepository.findTop20ByClusterUidOrderByTimestampDesc(clusterUid)
                .stream().map(a -> { Map<String,Object> m = new LinkedHashMap<>();
                    m.put("username", a.getUsername()); m.put("action", a.getAction());
                    m.put("details", a.getDetails()); m.put("timestamp", a.getTimestamp().toString()); return m; })
                .collect(Collectors.toList());
        Map<String, Object> auditSummary = new LinkedHashMap<>();
        auditSummary.put("totalEvents",  totalAuditEvents);
        auditSummary.put("topUsers",     topUsers);
        auditSummary.put("topActions",   topActions);
        auditSummary.put("recentLogs",   recentLogs);

        // ── Terminal sessions ─────────────────────────────────────────────────
        long terminalSessions = execSessionRepository.countByClusterId(clusterUid);

        // ── Compose report ────────────────────────────────────────────────────
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("clusterName",  cluster.getName());
        report.put("clusterUid",   clusterUid);
        report.put("generatedAt",  LocalDateTime.now().toString());
        report.put("resources",    resources);
        report.put("podHealth",    podHealth);
        report.put("nodeHealth",   nodeHealth);
        report.put("backupStatus", backupStatus);
        report.put("auditSummary", auditSummary);
        report.put("terminalSessions", terminalSessions);

        return ResponseEntity.ok(report);
    }

    // ── Resource detail lists (used by Excel export) ─────────────────────────

    @GetMapping("/resource-details")
    public ResponseEntity<Map<String, Object>> getResourceDetails(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "reports");

        var cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));
        Long clusterId = cluster.getId();

        // Nodes
        List<Map<String, Object>> nodeList = nodeRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                .stream().map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",      safe(n.getName()));
                    m.put("status",    safe(n.getStatus()));
                    m.put("roles",     safe(n.getRoles()));
                    m.put("cpu",       safe(n.getCpuCapacity()));
                    m.put("memory",    safe(n.getMemoryCapacity()));
                    m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
                    return m;
                }).collect(Collectors.toList());

        // Namespaces
        List<Map<String, Object>> nsList = namespaceRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                .stream().map(ns -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",      safe(ns.getName()));
                    m.put("status",    safe(ns.getStatus()));
                    m.put("createdAt", ns.getCreatedAt() != null ? ns.getCreatedAt().toString() : "");
                    return m;
                }).collect(Collectors.toList());

        // Pods
        List<Map<String, Object>> podList = podRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                .stream().map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",      safe(p.getName()));
                    m.put("namespace", safe(p.getNamespace()));
                    m.put("phase",     safe(p.getPhase()));
                    m.put("nodeName",  safe(p.getNodeName()));
                    m.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
                    return m;
                }).collect(Collectors.toList());

        // Deployments
        List<Map<String, Object>> deployList = deploymentRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                .stream().map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",               safe(d.getName()));
                    m.put("namespace",           safe(d.getNamespace()));
                    m.put("replicas",            d.getReplicas() != null ? d.getReplicas() : 0);
                    m.put("availableReplicas",   d.getAvailableReplicas() != null ? d.getAvailableReplicas() : 0);
                    m.put("createdAt",           d.getCreatedAt() != null ? d.getCreatedAt().toString() : "");
                    return m;
                }).collect(Collectors.toList());

        // Services
        List<Map<String, Object>> svcList = serviceRepository.findByClusterIdAndIsDeletedFalse(clusterId)
                .stream().map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",      safe(s.getName()));
                    m.put("namespace", safe(s.getNamespace()));
                    m.put("type",      safe(s.getServiceType()));
                    m.put("clusterIp", safe(s.getClusterIP()));
                    m.put("ports",     safe(s.getPorts()));
                    m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : "");
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes",       nodeList);
        result.put("namespaces",  nsList);
        result.put("pods",        podList);
        result.put("deployments", deployList);
        result.put("services",    svcList);
        return ResponseEntity.ok(result);
    }

    private String safe(Object v) { return v != null ? v.toString() : ""; }
}
