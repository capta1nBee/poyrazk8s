package com.k8s.platform.controller;

import com.k8s.platform.dto.request.federation.FederationRequest;
import com.k8s.platform.dto.response.federation.FederationResponse;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.federation.FederationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/federations")
@RequiredArgsConstructor
public class FederationController {

    private final FederationService federationService;
    private final ResourceAuthorizationHelper authHelper;

    @GetMapping
    public ResponseEntity<List<FederationResponse>> getAllFederations() {
        authHelper.checkPagePermissionOrThrow("federations");
        return ResponseEntity.ok(federationService.getAllFederations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FederationResponse> getFederation(@PathVariable Long id) {
        authHelper.checkPagePermissionOrThrow("federations");
        return ResponseEntity.ok(federationService.getFederation(id));
    }

    @PostMapping
    public ResponseEntity<FederationResponse> createFederation(@Valid @RequestBody FederationRequest request) {
        authHelper.checkPagePermissionOrThrow("federations");
        return ResponseEntity.status(HttpStatus.CREATED).body(federationService.createFederation(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FederationResponse> updateFederation(
            @PathVariable Long id,
            @Valid @RequestBody FederationRequest request) {
        authHelper.checkPagePermissionOrThrow("federations");
        return ResponseEntity.ok(federationService.updateFederation(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFederation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean removeFromMembers,
            @RequestParam(defaultValue = "false") boolean removeFromMaster) {
        authHelper.checkPagePermissionOrThrow("federations");
        federationService.deleteFederation(id, removeFromMembers, removeFromMaster);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<Void> syncFederation(@PathVariable Long id) {
        authHelper.checkPagePermissionOrThrow("federations");
        federationService.syncFederation(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resources/{resourceId}/rollback")
    public ResponseEntity<Void> rollbackResource(
            @PathVariable Long id,
            @PathVariable Long resourceId) {
        authHelper.checkPagePermissionOrThrow("federations");
        federationService.rollbackResource(id, resourceId);
        return ResponseEntity.ok().build();
    }
}
