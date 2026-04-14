package com.k8s.platform.service.helm;

import com.k8s.platform.domain.dto.helm.DeployRequest;
import com.k8s.platform.domain.dto.helm.DeployResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class HelmDeployService {

    private final HelmService helmService;
    private final DeploymentHistoryService historyService;
    private final HelmRepoService repoService;

    public CompletableFuture<DeployResponse> deployChart(DeployRequest request, String clusterUid) {
        String repoUrl = request.getRepoUrl();
        String username = null;
        String password = null;
        if (request.getRepoId() != null) {
            try {
                var repo = repoService.getEntityByIdAndCluster(request.getRepoId(), clusterUid);
                repoUrl = repo.getUrl();
                username = repo.getUsername();
                password = repo.getPassword();
            } catch (Exception e) {
                log.warn("Failed to get repository from ID: {}", e.getMessage());
            }
        }
        
        return helmService.installChartAsync(
                request.getDeployName(),
                request.getChartName(),
                request.getNamespace(),
                request.getChartVersion(),
                request.getCustomValues(),
                request.getCustomValuesYaml(),
                repoUrl,
                username,
                password,
                clusterUid
        ).whenComplete((output, ex) -> {
            if (ex != null) {
                historyService.record(clusterUid, DeploymentHistoryService.DeploymentRecord.builder()
                        .releaseName(request.getReleaseName())
                        .deployName(request.getDeployName())
                        .namespace(request.getNamespace())
                        .chartName(request.getChartName())
                        .chartVersion(request.getChartVersion())
                        .status("FAILED")
                        .timestamp(LocalDateTime.now())
                        .logs(ex.getMessage())
                        .build());
            } else {
                historyService.record(clusterUid, DeploymentHistoryService.DeploymentRecord.builder()
                        .releaseName(request.getReleaseName())
                        .deployName(request.getDeployName())
                        .namespace(request.getNamespace())
                        .chartName(request.getChartName())
                        .chartVersion(request.getChartVersion())
                        .status("DEPLOYED")
                        .timestamp(LocalDateTime.now())
                        .logs(output)
                        .build());
            }
        }).thenApply(output -> buildResponse(request, "DEPLOYED", output));
    }

    public CompletableFuture<DeployResponse> upgradeRelease(DeployRequest request, String clusterUid) {
        String releaseName = request.getReleaseName();
        String chartName = request.getChartName();
        String namespace = request.getNamespace();
        String version = request.getChartVersion();
        String customValuesYaml = request.getCustomValuesYaml();

        String repoUrl = request.getRepoUrl();
        String username = null;
        String password = null;
        if (request.getRepoId() != null) {
            try {
                var repo = repoService.getEntityByIdAndCluster(request.getRepoId(), clusterUid);
                repoUrl = repo.getUrl();
                username = repo.getUsername();
                password = repo.getPassword();
            } catch (Exception e) {
                log.warn("Failed to get repository from ID: {}", e.getMessage());
            }
        }

        return helmService.upgradeReleaseAsync(releaseName, chartName, namespace, version, customValuesYaml, repoUrl, username, password, clusterUid)
                .whenComplete((output, ex) -> {
                    if (ex != null) {
                        historyService.record(clusterUid, DeploymentHistoryService.DeploymentRecord.builder()
                                .releaseName(releaseName)
                                .namespace(namespace)
                                .chartName(chartName)
                                .chartVersion(version)
                                .status("FAILED")
                                .timestamp(LocalDateTime.now())
                                .logs(ex.getMessage())
                                .build());
                    } else {
                        historyService.record(clusterUid, DeploymentHistoryService.DeploymentRecord.builder()
                                .releaseName(releaseName)
                                .namespace(namespace)
                                .chartName(chartName)
                                .chartVersion(version)
                                .status("UPGRADED")
                                .timestamp(LocalDateTime.now())
                                .logs(output)
                                .build());
                    }
                }).thenApply(output -> DeployResponse.builder()
                        .success(true)
                        .releaseName(releaseName)
                        .namespace(namespace)
                        .status("UPGRADED")
                        .logs(output)
                        .build());
    }

    public CompletableFuture<DeployResponse> uninstallRelease(String releaseName, String namespace, String clusterUid) {
        return helmService.uninstallReleaseAsync(releaseName, namespace, clusterUid)
                .whenComplete((output, ex) -> {
                    if (ex != null) {
                        historyService.record(clusterUid, DeploymentHistoryService.DeploymentRecord.builder()
                                .releaseName(releaseName)
                                .namespace(namespace)
                                .status("FAILED")
                                .timestamp(LocalDateTime.now())
                                .logs(ex.getMessage())
                                .build());
                    } else {
                        historyService.record(clusterUid, DeploymentHistoryService.DeploymentRecord.builder()
                                .releaseName(releaseName)
                                .namespace(namespace)
                                .status("UNINSTALLED")
                                .timestamp(LocalDateTime.now())
                                .logs(output)
                                .build());
                    }
                }).thenApply(output -> DeployResponse.builder()
                        .success(true)
                        .releaseName(releaseName)
                        .namespace(namespace)
                        .status("UNINSTALLED")
                        .logs(output)
                        .build());
    }

    private DeployResponse buildResponse(DeployRequest request, String status, String logs) {
        return DeployResponse.builder()
                .success("DEPLOYED".equals(status))
                .releaseName(request.getReleaseName())
                .deployName(request.getDeployName())
                .namespace(request.getNamespace())
                .chartName(request.getChartName())
                .chartVersion(request.getChartVersion())
                .status(status)
                .logs(logs)
                .build();
    }
}
