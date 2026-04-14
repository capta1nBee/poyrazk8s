package com.k8s.platform.controller.appcreator;

import com.k8s.platform.domain.dto.appcreator.AppCreatorTemplateCreateRequest;
import com.k8s.platform.domain.dto.appcreator.AppCreatorTemplateDto;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.appcreator.AppCreatorTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/k8s/{clusterUid}/appcreator/templates")
@RequiredArgsConstructor
public class AppCreatorTemplateController {

    private final AppCreatorTemplateService templateService;
    private final ResourceAuthorizationHelper authHelper;

    @GetMapping
    public ResponseEntity<List<AppCreatorTemplateDto>> listTemplates(@PathVariable String clusterUid) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        return ResponseEntity.ok(templateService.listAvailable(clusterUid));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppCreatorTemplateDto> getTemplate(
            @PathVariable String clusterUid, @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    @PostMapping
    public ResponseEntity<AppCreatorTemplateDto> createTemplate(
            @PathVariable String clusterUid,
            @RequestBody AppCreatorTemplateCreateRequest request) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        Long userId = authHelper.getCurrentUser() != null ? authHelper.getCurrentUser().getId() : null;
        return ResponseEntity.ok(templateService.createTemplate(clusterUid, request, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppCreatorTemplateDto> updateTemplate(
            @PathVariable String clusterUid,
            @PathVariable UUID id,
            @RequestBody AppCreatorTemplateCreateRequest request) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable String clusterUid, @PathVariable UUID id) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "appcreator");
        templateService.deleteTemplate(id);
        return ResponseEntity.ok().build();
    }
}

