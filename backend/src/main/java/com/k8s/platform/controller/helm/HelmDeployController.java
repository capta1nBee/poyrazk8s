package com.k8s.platform.controller.helm;

import com.k8s.platform.domain.dto.helm.DeployRequest;
import com.k8s.platform.domain.dto.helm.DeployResponse;
import com.k8s.platform.service.helm.DeploymentHistoryService;
import com.k8s.platform.service.helm.HelmDeployService;
import com.k8s.platform.service.helm.HelmProxyService;
import com.k8s.platform.service.helm.HelmService;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/k8s/{clusterUid}/helm")
@RequiredArgsConstructor
public class HelmDeployController {

    private final HelmDeployService helmDeployService;
    private final DeploymentHistoryService historyService;
    private final HelmProxyService helmProxyService;
    private final HelmService helmService;
    private final ResourceAuthorizationHelper authHelper;

    // =========================
    // Deploy Chart
    // =========================
    @PostMapping("/deploy")
    public CompletableFuture<ResponseEntity<DeployResponse>> deployChart(
            @PathVariable String clusterUid,
            @RequestBody DeployRequest request) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");

        return helmDeployService.deployChart(request, clusterUid)
                .thenApply(ResponseEntity::ok);
    }

    // =========================
    // Upgrade Release
    // =========================
    @PostMapping("/upgrade")
    public CompletableFuture<ResponseEntity<DeployResponse>> upgradeRelease(
            @PathVariable String clusterUid,
            @RequestBody DeployRequest request) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");

        return helmDeployService.upgradeRelease(request, clusterUid)
                .thenApply(ResponseEntity::ok);
    }

    // =========================
    // Uninstall Release
    // =========================
    @DeleteMapping("/uninstall")
    public CompletableFuture<ResponseEntity<DeployResponse>> uninstallRelease(
            @PathVariable String clusterUid,
            @RequestParam String releaseName,
            @RequestParam String namespace) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");

        return helmDeployService.uninstallRelease(releaseName, namespace, clusterUid)
                .thenApply(ResponseEntity::ok);
    }

    // =========================
    // Deployment History
    // =========================
    @GetMapping("/history")
    public ResponseEntity<List<DeploymentHistoryService.DeploymentRecord>> getHistory(
            @PathVariable String clusterUid) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");
        List<DeploymentHistoryService.DeploymentRecord> history = historyService.getHistory(clusterUid);
        return ResponseEntity.ok(history);
    }

    // =========================
    // Live Helm Releases (helm list --all-namespaces)
    // =========================
    @GetMapping("/releases")
    public CompletableFuture<ResponseEntity<String>> listReleases(
            @PathVariable String clusterUid) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");

        log.info("[HELM-RELEASES] Permission granted, executing helm list on cluster={}", clusterUid);
        return helmService.listReleasesAsync(clusterUid)
                .thenApply(result -> ResponseEntity.ok(result))
                .exceptionally(ex -> {
                    log.error("[HELM-RELEASES] helm list FAILED — clusterUid={}, error={}", clusterUid, ex.getMessage(), ex);
                    return ResponseEntity.ok("[]");
                });
    }

    // =========================
    // Show Values for a Release
    // =========================
    @GetMapping("/releases/{releaseName}/values")
    public CompletableFuture<ResponseEntity<String>> getReleaseValues(
            @PathVariable String clusterUid,
            @PathVariable String releaseName,
            @RequestParam String namespace) {
        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");
        return helmService.getReleaseValuesAsync(releaseName, namespace, clusterUid)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.ok("# Could not retrieve values"));
    }

    // =========================
    // ArtifactHub Proxy
    // =========================
    @GetMapping("/packages/search")
    public ResponseEntity<String> searchPackages(
            @PathVariable String clusterUid,
            @RequestParam(required = false) Integer kind,
            @RequestParam(required = false) Integer category,
            @RequestParam(required = false) String ts_query_web,
            @RequestParam(required = false) Boolean official,
            @RequestParam(required = false) Boolean verified_publisher,
            @RequestParam(required = false) Boolean cncf,
            @RequestParam(required = false) Boolean deprecated,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");

        return helmProxyService.searchPackages(kind, category, ts_query_web, official, verified_publisher, cncf,
                deprecated, sort, direction);
    }

    @GetMapping("/packages/helm/{repoName}/{packageName}")
    public ResponseEntity<String> fetchHelmPackage(
            @PathVariable String clusterUid,
            @PathVariable String repoName,
            @PathVariable String packageName) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");
        return helmProxyService.fetchHelmPackage(repoName, packageName);
    }

    @GetMapping("/packages/{packageId}/{version}/values")
    public ResponseEntity<String> fetchValues(
            @PathVariable String clusterUid,
            @PathVariable String packageId,
            @PathVariable String version) {

        authHelper.checkPagePermissionOrThrow(clusterUid, "helm");
        return helmProxyService.fetchValues(packageId, version);
    }
}
