package com.k8s.platform.controller.appcreator;

import com.k8s.platform.domain.dto.appcreator.*;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.appcreator.AppCreatorDeployService;
import com.k8s.platform.service.appcreator.AppCreatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

@RestController
@RequestMapping("/api/k8s/{clusterUid}/appcreator")
@RequiredArgsConstructor
public class AppCreatorController {

    private final AppCreatorService appCreatorService;
    private final AppCreatorDeployService deployService;
    private final ResourceAuthorizationHelper authHelper;

    // ── App CRUD ──────────────────────────────────────────────────────────────

    @GetMapping("/apps")
    public ResponseEntity<List<AppCreatorAppDto>> listApps(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        return ResponseEntity.ok(appCreatorService.listApps(clusterUid));
    }

    @GetMapping("/apps/{id}")
    public ResponseEntity<AppCreatorAppDto> getApp(@PathVariable String clusterUid, @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        return ResponseEntity.ok(appCreatorService.getApp(clusterUid, id));
    }

    @PostMapping("/apps")
    public ResponseEntity<AppCreatorAppDto> createApp(
            @PathVariable String clusterUid,
            @RequestBody AppCreatorCreateRequest request) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        Long userId = authHelper.getCurrentUser() != null ? authHelper.getCurrentUser().getId() : null;
        return ResponseEntity.ok(appCreatorService.createApp(clusterUid, request, userId));
    }

    @PutMapping("/apps/{id}")
    public ResponseEntity<AppCreatorAppDto> updateApp(
            @PathVariable String clusterUid,
            @PathVariable UUID id,
            @RequestBody AppCreatorCreateRequest request) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        Long userId = authHelper.getCurrentUser() != null ? authHelper.getCurrentUser().getId() : null;
        return ResponseEntity.ok(appCreatorService.updateApp(clusterUid, id, request, userId));
    }

    @GetMapping("/apps/{id}/k8s-resources")
    public ResponseEntity<List<AppCreatorK8sResourceDto>> listK8sResources(
            @PathVariable String clusterUid, @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        try {
            return ResponseEntity.ok(appCreatorService.listK8sResources(clusterUid, id));
        } catch (Exception e) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @DeleteMapping("/apps/{id}")
    public ResponseEntity<Void> deleteApp(
            @PathVariable String clusterUid,
            @PathVariable UUID id,
            @RequestParam(name = "deleteK8sResources", defaultValue = "false") boolean deleteK8sResources) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        appCreatorService.deleteApp(clusterUid, id, deleteK8sResources);
        return ResponseEntity.ok().build();
    }

    // ── YAML Preview ──────────────────────────────────────────────────────────

    @GetMapping("/apps/{id}/yaml")
    public ResponseEntity<AppCreatorYamlPreviewResponse> previewYaml(
            @PathVariable String clusterUid, @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        return ResponseEntity.ok(appCreatorService.previewYaml(clusterUid, id));
    }

    @PostMapping("/preview-yaml")
    public ResponseEntity<AppCreatorYamlPreviewResponse> previewYamlFromConfig(
            @PathVariable String clusterUid,
            @RequestBody Map<String, String> body) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        String config = body.get("config");
        return ResponseEntity.ok(appCreatorService.previewYamlFromConfig(config));
    }

    // ── Deploy ────────────────────────────────────────────────────────────────

    @PostMapping("/apps/{id}/deploy")
    public ResponseEntity<AppCreatorDeployResult> deploy(
            @PathVariable String clusterUid,
            @PathVariable UUID id,
            @RequestBody AppCreatorDeployRequest request) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        Long userId = authHelper.getCurrentUser() != null ? authHelper.getCurrentUser().getId() : null;
        return ResponseEntity.ok(deployService.deploy(clusterUid, id, request, userId));
    }

    @GetMapping("/apps/{id}/history")
    public ResponseEntity<List<AppCreatorDeployResult>> getHistory(
            @PathVariable String clusterUid, @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        return ResponseEntity.ok(deployService.getHistory(clusterUid, id));
    }

    // ── Draft ─────────────────────────────────────────────────────────────────

    @PostMapping("/drafts")
    public ResponseEntity<AppCreatorDraftDto> saveDraft(
            @PathVariable String clusterUid,
            @RequestBody Map<String, Object> body) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        Long userId = authHelper.getCurrentUser() != null ? authHelper.getCurrentUser().getId() : null;
        UUID draftId = body.containsKey("draftId") ? UUID.fromString(body.get("draftId").toString()) : null;
        String state = body.get("wizardState").toString();
        int step = body.containsKey("currentStep") ? Integer.parseInt(body.get("currentStep").toString()) : 1;
        return ResponseEntity.ok(appCreatorService.saveDraft(clusterUid, draftId, state, step, userId));
    }

    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<AppCreatorDraftDto> getDraft(
            @PathVariable String clusterUid, @PathVariable UUID draftId) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        return ResponseEntity.ok(appCreatorService.getDraft(clusterUid, draftId));
    }

    @DeleteMapping("/drafts/{draftId}")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable String clusterUid, @PathVariable UUID draftId) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        appCreatorService.deleteDraft(clusterUid, draftId);
        return ResponseEntity.ok().build();
    }
}

