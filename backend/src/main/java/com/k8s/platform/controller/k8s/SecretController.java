package com.k8s.platform.controller.k8s;

import com.k8s.platform.controller.response.SecretResponseDTO;
import com.k8s.platform.service.k8s.SecretService;
import io.fabric8.kubernetes.api.model.Secret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class SecretController {

    private final SecretService secretService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    private SecretResponseDTO toDTO(Secret secret) {
        if (secret == null)
            return null;

        List<String> keys = new ArrayList<>();
        if (secret.getData() != null) {
            keys.addAll(secret.getData().keySet());
        }

        String age = "";
        if (secret.getMetadata() != null && secret.getMetadata().getCreationTimestamp() != null) {
            try {
                age = calculateAge(secret.getMetadata().getCreationTimestamp());
            } catch (Exception e) {
                log.warn("Failed to calculate age", e);
            }
        }

        return SecretResponseDTO.builder()
                .id(secret.getMetadata() != null ? secret.getMetadata().getUid() : "")
                .name(secret.getMetadata() != null ? secret.getMetadata().getName() : "")
                .namespace(secret.getMetadata() != null ? secret.getMetadata().getNamespace() : "")
                .type(secret.getType() != null ? secret.getType() : "Opaque")
                .keys(keys)
                .age(age)
                .yaml(secret.toString())
                .build();
    }

    private String calculateAge(String creationTimestamp) {
        try {
            LocalDateTime created = LocalDateTime.parse(creationTimestamp.replace("Z", "+00:00"),
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

            long seconds = java.time.temporal.ChronoUnit.SECONDS.between(created, now);

            if (seconds < 60)
                return seconds + "s";
            if (seconds < 3600)
                return (seconds / 60) + "m";
            if (seconds < 86400)
                return (seconds / 3600) + "h";
            return (seconds / 86400) + "d";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * List all secrets - filtered by authorized namespaces
     */
    @GetMapping("/secrets")
    public ResponseEntity<List<SecretResponseDTO>> listAllSecrets(
            @PathVariable String clusterUid,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {

        authHelper.checkPermissionOrThrow(clusterUid, "*", "Secret", "*", "view");

        List<Secret> secrets = secretService.listSecrets(clusterUid, includeDeleted);
        List<SecretResponseDTO> dtos = new ArrayList<>();
        for (Secret secret : secrets) {
            dtos.add(toDTO(secret));
        }

        // Filter by authorized namespaces
        List<SecretResponseDTO> filteredDtos = authHelper.filterAccessibleResources(dtos, clusterUid, "Secret", "view",
                SecretResponseDTO::getNamespace, SecretResponseDTO::getName);

        return ResponseEntity.ok(filteredDtos);
    }

    @GetMapping("/namespaces/{namespace}/secrets")
    public ResponseEntity<List<SecretResponseDTO>> listSecrets(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Secret", "*", "view");

        List<Secret> secrets = secretService.listSecrets(clusterUid, namespace, includeDeleted);
        List<SecretResponseDTO> dtos = new ArrayList<>();
        for (Secret secret : secrets) {
            dtos.add(toDTO(secret));
        }
        return ResponseEntity.ok(authHelper.filterAccessibleResources(dtos, clusterUid, "Secret", "view",
                SecretResponseDTO::getNamespace, SecretResponseDTO::getName));
    }

    @GetMapping("/namespaces/{namespace}/secrets/{name}")
    public ResponseEntity<Secret> getSecret(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Secret", name, "view");

        return ResponseEntity.ok(secretService.getSecret(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/secrets")
    public ResponseEntity<Secret> createSecret(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestBody Secret secret) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Secret", "*", "create");

        return ResponseEntity.ok(secretService.createSecret(clusterUid, namespace, secret));
    }

    @PutMapping("/namespaces/{namespace}/secrets/{name}")
    public ResponseEntity<Secret> updateSecret(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Secret secret) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Secret", name, "update");

        return ResponseEntity.ok(secretService.updateSecret(clusterUid, namespace, name, secret));
    }

    @DeleteMapping("/namespaces/{namespace}/secrets/{name}")
    public ResponseEntity<Map<String, String>> deleteSecret(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Secret", name, "delete");

        secretService.deleteSecret(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Secret deleted successfully"));
    }

    @GetMapping("/namespaces/{namespace}/secrets/{name}/reveal")
    public ResponseEntity<Map<String, String>> revealSecret(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Secret", name, "reveal");

        return ResponseEntity.ok(secretService.revealSecret(clusterUid, namespace, name));
    }

    @GetMapping("/namespaces/{namespace}/secrets/{name}/data")
    public ResponseEntity<Map<String, String>> getSecretData(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Secret", name, "view");

        return ResponseEntity.ok(secretService.revealSecret(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/secrets/{name}/rotate")
    public ResponseEntity<Secret> rotateSecret(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Map<String, String> newData) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Secret", name, "rotate");

        return ResponseEntity.ok(secretService.rotateSecret(clusterUid, namespace, name, newData));
    }
}
