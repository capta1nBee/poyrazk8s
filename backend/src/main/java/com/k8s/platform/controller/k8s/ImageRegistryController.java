package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.ImageRegistryCredential;
import com.k8s.platform.domain.repository.ImageRegistryCredentialRepository;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRUD API for image registry credentials per cluster.
 * Uses the same page-level permission as clusters management.
 */
@RestController
@RequestMapping("/api/k8s/{clusterUid}/registry-credentials")
@RequiredArgsConstructor
public class ImageRegistryController {

    private final ImageRegistryCredentialRepository repository;
    private final ResourceAuthorizationHelper authHelper;

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "clusters");
        return repository.findByClusterUidOrderByRegistryUrlAsc(clusterUid).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, Object> create(@PathVariable String clusterUid,
                                      @RequestBody Map<String, String> body) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "clusters");

        String registryUrl = body.get("registryUrl");
        String username = body.get("username");
        String password = body.get("password");
        String description = body.getOrDefault("description", "");

        if (registryUrl == null || registryUrl.isBlank() || username == null || password == null) {
            throw new IllegalArgumentException("registryUrl, username, and password are required");
        }

        // Check duplicate
        if (repository.findByClusterUidAndRegistryUrl(clusterUid, registryUrl.trim()).isPresent()) {
            throw new IllegalArgumentException("Registry '" + registryUrl + "' already exists for this cluster");
        }

        ImageRegistryCredential cred = ImageRegistryCredential.builder()
                .clusterUid(clusterUid)
                .registryUrl(registryUrl.trim())
                .username(username)
                .password(password)
                .description(description)
                .build();

        return toDto(repository.save(cred));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String clusterUid,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, String> body) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "clusters");

        ImageRegistryCredential cred = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registry credential not found"));

        if (!cred.getClusterUid().equals(clusterUid)) {
            throw new IllegalArgumentException("Registry credential does not belong to this cluster");
        }

        if (body.containsKey("registryUrl")) cred.setRegistryUrl(body.get("registryUrl").trim());
        if (body.containsKey("username")) cred.setUsername(body.get("username"));
        if (body.containsKey("password")) cred.setPassword(body.get("password"));
        if (body.containsKey("description")) cred.setDescription(body.get("description"));

        return toDto(repository.save(cred));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> delete(@PathVariable String clusterUid,
                                                       @PathVariable Long id) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "clusters");

        ImageRegistryCredential cred = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registry credential not found"));

        if (!cred.getClusterUid().equals(clusterUid)) {
            throw new IllegalArgumentException("Registry credential does not belong to this cluster");
        }

        repository.delete(cred);
        return ResponseEntity.ok(Map.of("message", "Registry credential deleted successfully"));
    }

    private Map<String, Object> toDto(ImageRegistryCredential c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("clusterUid", c.getClusterUid());
        m.put("registryUrl", c.getRegistryUrl());
        m.put("username", c.getUsername());
        // Password is masked for security
        m.put("password", "••••••••");
        m.put("description", c.getDescription());
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        m.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
        return m;
    }
}
