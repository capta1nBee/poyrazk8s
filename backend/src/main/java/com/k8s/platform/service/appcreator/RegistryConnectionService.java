package com.k8s.platform.service.appcreator;

import com.k8s.platform.domain.dto.appcreator.RegistryConnectionCreateRequest;
import com.k8s.platform.domain.dto.appcreator.RegistryConnectionDto;
import com.k8s.platform.domain.entity.appcreator.RegistryConnection;
import com.k8s.platform.repository.appcreator.RegistryConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryConnectionService {

    private final RegistryConnectionRepository repository;

    /** Lazy injection to break the circular dependency with ContainerBuildService */
    @Lazy
    @Autowired
    private ContainerBuildService containerBuildService;

    public RegistryConnectionDto update(String clusterUid, UUID id, Long userId, RegistryConnectionCreateRequest req) {
        RegistryConnection conn = repository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("Registry connection not found: " + id));
        conn.setRegistryType(req.getRegistryType());
        conn.setName(req.getName());
        conn.setServerUrl(resolveServerUrl(req.getRegistryType(), req.getServerUrl()));
        conn.setUsername(req.getUsername());
        if (req.getPasswordToken() != null && !req.getPasswordToken().isBlank()) {
            conn.setPasswordToken(req.getPasswordToken());
        }
        conn.setImagePrefix(req.getImagePrefix());
        conn.setIsDefault(Boolean.TRUE.equals(req.getIsDefault()));
        RegistryConnection saved = repository.save(conn);
        containerBuildService.loginRegistry(saved);
        return toDto(saved);
    }

    public RegistryConnectionDto create(String clusterUid, Long userId, RegistryConnectionCreateRequest req) {
        RegistryConnection conn = RegistryConnection.builder()
                .clusterUid(clusterUid)
                .userId(userId)
                .registryType(req.getRegistryType())
                .name(req.getName())
                .serverUrl(resolveServerUrl(req.getRegistryType(), req.getServerUrl()))
                .username(req.getUsername())
                .passwordToken(req.getPasswordToken())
                .imagePrefix(req.getImagePrefix())
                .isDefault(Boolean.TRUE.equals(req.getIsDefault()))
                .build();
        RegistryConnection saved = repository.save(conn);
        // Immediately login to the registry so it's ready for builds
        containerBuildService.loginRegistry(saved);
        return toDto(saved);
    }

    /**
     * On application startup, re-login to all stored registries.
     * Needed because the DinD daemon resets its auth state when restarted.
     */
    @EventListener(ContextRefreshedEvent.class)
    public void loginAllRegistriesOnStartup() {
        List<RegistryConnection> all = repository.findAll();
        log.info("[REGISTRY-LOGIN] Startup: logging in to {} stored registry connection(s)", all.size());
        for (RegistryConnection conn : all) {
            containerBuildService.loginRegistry(conn);
        }
    }

    /** List ALL connections for a cluster — visible to any authorized user */
    public List<RegistryConnectionDto> listForCluster(String clusterUid, Long userId) {
        return repository.findAllByClusterUidOrderByCreatedAtDesc(clusterUid)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public void delete(String clusterUid, UUID id, Long userId) {
        RegistryConnection conn = repository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("Registry connection not found: " + id));
        repository.delete(conn);
    }

    public RegistryConnection getConn(String clusterUid, UUID id, Long userId) {
        return repository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("Registry connection not found: " + id));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String resolveServerUrl(String registryType, String serverUrl) {
        if (serverUrl != null && !serverUrl.isBlank()) return serverUrl.trim();
        return switch (registryType) {
            case "github"    -> "ghcr.io";
            case "gitlab"    -> "registry.gitlab.com";
            case "dockerhub" -> "index.docker.io";
            default          -> null;
        };
    }

    private RegistryConnectionDto toDto(RegistryConnection c) {
        return RegistryConnectionDto.builder()
                .id(c.getId())
                .registryType(c.getRegistryType())
                .name(c.getName())
                .serverUrl(c.getServerUrl())
                .username(c.getUsername())
                .imagePrefix(c.getImagePrefix())
                .isDefault(c.getIsDefault())
                .createdAt(c.getCreatedAt())
                .build();
    }
}

