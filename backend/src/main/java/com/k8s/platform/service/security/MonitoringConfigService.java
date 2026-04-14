package com.k8s.platform.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.MonitoringConfig;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.MonitoringConfigRepository;
import com.k8s.platform.dto.response.MonitoringConfigResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringConfigService {

    private final MonitoringConfigRepository monitoringConfigRepository;
    private final ObjectMapper objectMapper;

    /**
     * Get monitoring config for agent
     */
    public Map<String, Object> getMonitoringConfig(String clusterUid) {
        log.info("Fetching monitoring config for cluster: {}", clusterUid);

        MonitoringConfig config = monitoringConfigRepository.findByClusterUid(clusterUid)
                .orElse(getDefaultConfig(clusterUid));

        return convertToAgentFormat(config);
    }

    /**
     * Get monitoring config as response DTO
     */
    public MonitoringConfigResponse getMonitoringConfigResponse(String clusterUid) {
        log.info("Fetching monitoring config response for cluster: {}", clusterUid);

        MonitoringConfig config = monitoringConfigRepository.findByClusterUid(clusterUid)
                .orElse(getDefaultConfig(clusterUid));

        return MonitoringConfigResponse.fromEntity(config);
    }

    /**
     * Create or update monitoring config
     */
    @Transactional
    public MonitoringConfigResponse createOrUpdateConfig(String clusterUid, MonitoringConfig newConfig, User updatedBy) {
        log.info("Creating/updating monitoring config for cluster: {}", clusterUid);

        MonitoringConfig config = monitoringConfigRepository.findByClusterUid(clusterUid)
                .orElse(MonitoringConfig.builder()
                        .clusterUid(clusterUid)
                        .build());

        config.updateConfiguration(newConfig, updatedBy.getId());
        MonitoringConfig savedConfig = monitoringConfigRepository.save(config);

        log.info("Monitoring config updated for cluster: {}", clusterUid);

        return MonitoringConfigResponse.fromEntity(savedConfig);
    }

    /**
     * Enable specific tracepoint
     */
    @Transactional
    public MonitoringConfigResponse enableTracepoint(String clusterUid, String tracepointType, User updatedBy) {
        log.info("Enabling tracepoint: {} for cluster: {}", tracepointType, clusterUid);

        MonitoringConfig config = monitoringConfigRepository.findByClusterUid(clusterUid)
                .orElse(getDefaultConfig(clusterUid));

        switch (tracepointType.toLowerCase()) {
            case "execve" -> config.setEnableExecve(true);
            case "open" -> config.setEnableOpen(true);
            case "openat" -> config.setEnableOpenat(true);
            case "connect" -> config.setEnableConnect(true);
            case "bind" -> config.setEnableBind(true);
            case "unlink" -> config.setEnableUnlink(true);
            case "unlinkat" -> config.setEnableUnlinkat(true);
            case "write" -> config.setEnableWrite(true);
            case "link" -> config.setEnableLink(true);
            case "rename" -> config.setEnableRename(true);
            case "mkdir" -> config.setEnableMkdir(true);
            case "rmdir" -> config.setEnableRmdir(true);
            case "xattr" -> config.setEnableXattr(true);
            case "clone" -> config.setEnableClone(true);
            case "fork" -> config.setEnableFork(true);
            case "accept" -> config.setEnableAccept(true);
            case "ptrace" -> config.setEnablePtrace(true);
            case "mount" -> config.setEnableMount(true);
            default -> log.warn("Unknown tracepoint type: {}", tracepointType);
        }

        config.setUpdatedBy(updatedBy.getId());
        MonitoringConfig savedConfig = monitoringConfigRepository.save(config);

        log.info("Tracepoint enabled: {} for cluster: {}", tracepointType, clusterUid);

        return MonitoringConfigResponse.fromEntity(savedConfig);
    }

    /**
     * Disable specific tracepoint
     */
    @Transactional
    public MonitoringConfigResponse disableTracepoint(String clusterUid, String tracepointType, User updatedBy) {
        log.info("Disabling tracepoint: {} for cluster: {}", tracepointType, clusterUid);

        MonitoringConfig config = monitoringConfigRepository.findByClusterUid(clusterUid)
                .orElse(getDefaultConfig(clusterUid));

        switch (tracepointType.toLowerCase()) {
            case "execve" -> config.setEnableExecve(false);
            case "open" -> config.setEnableOpen(false);
            case "openat" -> config.setEnableOpenat(false);
            case "connect" -> config.setEnableConnect(false);
            case "bind" -> config.setEnableBind(false);
            case "unlink" -> config.setEnableUnlink(false);
            case "unlinkat" -> config.setEnableUnlinkat(false);
            case "write" -> config.setEnableWrite(false);
            case "link" -> config.setEnableLink(false);
            case "rename" -> config.setEnableRename(false);
            case "mkdir" -> config.setEnableMkdir(false);
            case "rmdir" -> config.setEnableRmdir(false);
            case "xattr" -> config.setEnableXattr(false);
            case "clone" -> config.setEnableClone(false);
            case "fork" -> config.setEnableFork(false);
            case "accept" -> config.setEnableAccept(false);
            case "ptrace" -> config.setEnablePtrace(false);
            case "mount" -> config.setEnableMount(false);
            default -> log.warn("Unknown tracepoint type: {}", tracepointType);
        }

        config.setUpdatedBy(updatedBy.getId());
        MonitoringConfig savedConfig = monitoringConfigRepository.save(config);

        log.info("Tracepoint disabled: {} for cluster: {}", tracepointType, clusterUid);

        return MonitoringConfigResponse.fromEntity(savedConfig);
    }

    /**
     * Convert MonitoringConfig to agent format
     */
    private Map<String, Object> convertToAgentFormat(MonitoringConfig config) {
        Map<String, Object> agentConfig = new HashMap<>();
        agentConfig.put("enable_execve", config.getEnableExecve());
        agentConfig.put("enable_open", config.getEnableOpen());
        agentConfig.put("enable_openat", config.getEnableOpenat());
        agentConfig.put("enable_connect", config.getEnableConnect());
        agentConfig.put("enable_bind", config.getEnableBind());
        agentConfig.put("enable_unlink", config.getEnableUnlink());
        agentConfig.put("enable_unlinkat", config.getEnableUnlinkat());
        agentConfig.put("enable_write", config.getEnableWrite());
        agentConfig.put("enable_link", config.getEnableLink());
        agentConfig.put("enable_rename", config.getEnableRename());
        agentConfig.put("enable_mkdir", config.getEnableMkdir());
        agentConfig.put("enable_rmdir", config.getEnableRmdir());
        agentConfig.put("enable_xattr", config.getEnableXattr());
        agentConfig.put("enable_clone", config.getEnableClone());
        agentConfig.put("enable_fork", config.getEnableFork());
        agentConfig.put("enable_accept", config.getEnableAccept());
        agentConfig.put("enable_ptrace", config.getEnablePtrace());
        agentConfig.put("enable_mount", config.getEnableMount());

        if (config.getAdditionalConfigJson() != null) {
            try {
                Map<String, Object> additional = objectMapper.readValue(
                        config.getAdditionalConfigJson(), Map.class);
                agentConfig.putAll(additional);
            } catch (Exception e) {
                log.error("Failed to parse additional config", e);
            }
        }

        return agentConfig;
    }

    /**
     * Get default monitoring config
     */
    private MonitoringConfig getDefaultConfig(String clusterUid) {
        return MonitoringConfig.builder()
                .clusterUid(clusterUid)
                .enableExecve(false)
                .enableOpen(false)
                .enableOpenat(false)
                .enableConnect(false)
                .enableBind(false)
                .enableUnlink(false)
                .enableUnlinkat(false)
                .enableWrite(false)
                .enableLink(false)
                .enableRename(false)
                .enableMkdir(false)
                .enableRmdir(false)
                .enableXattr(false)
                .enableClone(false)
                .enableFork(false)
                .enableAccept(false)
                .enablePtrace(false)
                .enableMount(false)
                .build();
    }
}
