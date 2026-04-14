package com.k8s.platform.controller.k8s;

import com.k8s.platform.controller.response.ConfigMapResponseDTO;
import com.k8s.platform.service.k8s.ConfigMapService;
import io.fabric8.kubernetes.api.model.ConfigMap;
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
public class ConfigMapController {

    private final ConfigMapService configMapService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    private ConfigMapResponseDTO toDTO(ConfigMap configMap) {
        if (configMap == null)
            return null;

        List<String> keys = new ArrayList<>();
        if (configMap.getData() != null) {
            keys.addAll(configMap.getData().keySet());
        }

        String age = "";
        if (configMap.getMetadata() != null && configMap.getMetadata().getCreationTimestamp() != null) {
            try {
                age = calculateAge(configMap.getMetadata().getCreationTimestamp());
            } catch (Exception e) {
                log.warn("Failed to calculate age", e);
            }
        }

        return ConfigMapResponseDTO.builder()
                .id(configMap.getMetadata() != null ? configMap.getMetadata().getUid() : "")
                .name(configMap.getMetadata() != null ? configMap.getMetadata().getName() : "")
                .namespace(configMap.getMetadata() != null ? configMap.getMetadata().getNamespace() : "")
                .keys(keys)
                .age(age)
                .yaml(configMap.toString())
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
     * List all configmaps - filtered by authorized namespaces
     */
    @GetMapping("/configmaps")
    public ResponseEntity<List<ConfigMapResponseDTO>> listAllConfigMaps(
            @PathVariable String clusterUid,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {

        authHelper.checkPermissionOrThrow(clusterUid, "*", "ConfigMap", "*", "view");

        List<ConfigMap> configMaps = configMapService.listConfigMaps(clusterUid, includeDeleted);
        List<ConfigMapResponseDTO> dtos = new ArrayList<>();
        for (ConfigMap configMap : configMaps) {
            dtos.add(toDTO(configMap));
        }

        // Filter by authorized namespaces
        List<ConfigMapResponseDTO> filteredDtos = authHelper.filterAccessibleResources(dtos, clusterUid, "ConfigMap",
                "view", ConfigMapResponseDTO::getNamespace, ConfigMapResponseDTO::getName);

        return ResponseEntity.ok(filteredDtos);
    }

    @GetMapping("/namespaces/{namespace}/configmaps")
    public ResponseEntity<List<ConfigMapResponseDTO>> listConfigMaps(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ConfigMap", "*", "view");

        List<ConfigMap> configMaps = configMapService.listConfigMaps(clusterUid, namespace, includeDeleted);
        List<ConfigMapResponseDTO> dtos = new ArrayList<>();
        for (ConfigMap configMap : configMaps) {
            dtos.add(toDTO(configMap));
        }
        return ResponseEntity.ok(authHelper.filterAccessibleResources(dtos, clusterUid, "ConfigMap", "view",
                ConfigMapResponseDTO::getNamespace, ConfigMapResponseDTO::getName));
    }

    @GetMapping("/namespaces/{namespace}/configmaps/{name}")
    public ResponseEntity<ConfigMap> getConfigMap(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ConfigMap", name, "view");

        return ResponseEntity.ok(configMapService.getConfigMap(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/configmaps")
    public ResponseEntity<ConfigMap> createConfigMap(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestBody ConfigMap configMap) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ConfigMap", "*", "create");

        return ResponseEntity.ok(configMapService.createConfigMap(clusterUid, namespace, configMap));
    }

    @PutMapping("/namespaces/{namespace}/configmaps/{name}")
    public ResponseEntity<ConfigMap> updateConfigMap(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody ConfigMap configMap) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ConfigMap", name, "update");

        return ResponseEntity.ok(configMapService.updateConfigMap(clusterUid, namespace, name, configMap));
    }

    @DeleteMapping("/namespaces/{namespace}/configmaps/{name}")
    public ResponseEntity<Map<String, String>> deleteConfigMap(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ConfigMap", name, "delete");

        configMapService.deleteConfigMap(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "ConfigMap deleted successfully"));
    }

    @PostMapping("/namespaces/{namespace}/configmaps/{name}/update-data")
    public ResponseEntity<ConfigMap> updateData(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody Map<String, String> newData) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ConfigMap", name, "update");

        return ResponseEntity.ok(configMapService.updateData(clusterUid, namespace, name, newData));
    }

    @GetMapping("/namespaces/{namespace}/configmaps/{name}/mount-preview")
    public ResponseEntity<Map<String, Object>> getMountPreview(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ConfigMap", name, "view");

        return ResponseEntity.ok(configMapService.getMountPreview(clusterUid, namespace, name));
    }

    @GetMapping("/namespaces/{namespace}/configmaps/{name}/data")
    public ResponseEntity<Map<String, String>> getConfigMapData(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "ConfigMap", name, "view");

        ConfigMap configMap = configMapService.getConfigMap(clusterUid, namespace, name);
        if (configMap != null && configMap.getData() != null) {
            return ResponseEntity.ok(configMap.getData());
        }
        return ResponseEntity.ok(Map.of());
    }
}
