package com.k8s.platform.service.appcreator;

import com.k8s.platform.domain.dto.appcreator.AppCreatorDeployRequest;
import com.k8s.platform.domain.dto.appcreator.AppCreatorDeployResult;
import com.k8s.platform.domain.entity.appcreator.AppCreatorApp;
import com.k8s.platform.domain.entity.appcreator.AppCreatorDeployHistory;
import com.k8s.platform.domain.entity.appcreator.GitConnection;
import com.k8s.platform.repository.appcreator.AppCreatorAppRepository;
import com.k8s.platform.repository.appcreator.AppCreatorDeployHistoryRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppCreatorDeployService {

    private final AppCreatorAppRepository appRepository;
    private final AppCreatorDeployHistoryRepository historyRepository;
    private final AppCreatorYamlGenerator yamlGenerator;
    private final ClusterContextManager clusterContextManager;
    private final GitService gitService;

    @Transactional
    public AppCreatorDeployResult deploy(String clusterUid, UUID appId, AppCreatorDeployRequest req, Long userId) {
        AppCreatorApp app = appRepository.findByIdAndClusterUid(appId, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("App not found: " + appId));

        Map<String, String> yamlFiles = yamlGenerator.generate(app.getConfig());
        String combinedYaml = yamlFiles.values().stream()
                .collect(Collectors.joining("\n---\n"));

        String deployType = req.getDeployType() != null ? req.getDeployType() : "direct";
        AppCreatorDeployHistory history;

        if ("git".equals(deployType)) {
            history = deployViaGit(app, req, yamlFiles, combinedYaml, userId);
        } else {
            history = deployDirect(clusterUid, app, yamlFiles, combinedYaml, userId);
        }

        // Update app status
        app.setStatus("DEPLOYED");
        appRepository.save(app);

        return AppCreatorDeployResult.builder()
                .historyId(history.getId())
                .status(history.getStatus())
                .deployType(history.getDeployType())
                .gitPrUrl(history.getGitPrUrl())
                .gitCommitSha(history.getGitCommitSha())
                .resourceCount(history.getResourceCount())
                .errorMessage(history.getErrorMessage())
                .createdAt(history.getCreatedAt())
                .build();
    }

    private AppCreatorDeployHistory deployDirect(String clusterUid, AppCreatorApp app,
            Map<String, String> yamlFiles, String combinedYaml, Long userId) {
        String status = "SUCCESS";
        String errorMsg = null;
        int count = 0;

        try {
            KubernetesClient client = clusterContextManager.getClient(clusterUid);
            for (Map.Entry<String, String> entry : yamlFiles.entrySet()) {
                try (ByteArrayInputStream is = new ByteArrayInputStream(
                        entry.getValue().getBytes(StandardCharsets.UTF_8))) {
                    client.load(is).serverSideApply();
                    count++;
                    log.info("Applied {} for app {}", entry.getKey(), app.getName());
                } catch (Exception e) {
                    log.error("Failed to apply {}: {}", entry.getKey(), e.getMessage());
                    throw e;
                }
            }
        } catch (Exception e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            log.error("Direct deploy failed for app {}: {}", app.getName(), e.getMessage());
        }

        return historyRepository.save(AppCreatorDeployHistory.builder()
                .appId(app.getId())
                .clusterUid(clusterUid)
                .deployType("direct")
                .status(status)
                .resourceCount(count)
                .yamlSnapshot(combinedYaml)
                .errorMessage(errorMsg)
                .deployedBy(userId)
                .build());
    }

    private AppCreatorDeployHistory deployViaGit(AppCreatorApp app, AppCreatorDeployRequest req,
            Map<String, String> yamlFiles, String combinedYaml, Long userId) {

        if (req.getGitConnectionId() == null) {
            throw new IllegalArgumentException("gitConnectionId is required for git deploy");
        }
        if (req.getGitRepo() == null || req.getGitRepo().isBlank()) {
            throw new IllegalArgumentException("gitRepo is required for git deploy (format: owner/repo)");
        }

        String baseBranch = req.getGitBranch() != null ? req.getGitBranch() : "main";
        String gitPath    = req.getGitPath()   != null ? req.getGitPath()   : "k8s";

        String prUrl   = null;
        String status  = "SUCCESS";
        String errMsg  = null;

        try {
            GitConnection conn = gitService.getConn(app.getClusterUid(), req.getGitConnectionId(), userId);
            String[] parts = req.getGitRepo().split("/", 2);
            if (parts.length != 2) throw new IllegalArgumentException("gitRepo must be in owner/repo format");
            String owner = parts[0];
            String repo  = parts[1];

            log.info("Git deploy: pushing {} YAML files for app '{}' → {}/{} branch={} path={}",
                    yamlFiles.size(), app.getName(), owner, repo, baseBranch, gitPath);

            prUrl = gitService.pushYamlAndCreatePR(conn, owner, repo, baseBranch,
                    app.getName(), yamlFiles, gitPath);

            log.info("Git deploy: PR/MR created at {}", prUrl);
        } catch (Exception e) {
            status = "FAILED";
            errMsg = e.getMessage();
            log.error("Git deploy failed for app '{}': {}", app.getName(), e.getMessage(), e);
        }

        return historyRepository.save(AppCreatorDeployHistory.builder()
                .appId(app.getId())
                .clusterUid(app.getClusterUid())
                .deployType("git")
                .status(status)
                .gitRepo(req.getGitRepo())
                .gitBranch(baseBranch)
                .gitPrUrl(prUrl)
                .resourceCount(yamlFiles.size())
                .yamlSnapshot(combinedYaml)
                .errorMessage(errMsg)
                .deployedBy(userId)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AppCreatorDeployResult> getHistory(String clusterUid, UUID appId) {
        return historyRepository.findAllByAppIdOrderByCreatedAtDesc(appId)
                .stream().map(h -> AppCreatorDeployResult.builder()
                        .historyId(h.getId()).status(h.getStatus()).deployType(h.getDeployType())
                        .gitPrUrl(h.getGitPrUrl()).gitCommitSha(h.getGitCommitSha())
                        .resourceCount(h.getResourceCount()).errorMessage(h.getErrorMessage())
                        .createdAt(h.getCreatedAt()).build())
                .collect(Collectors.toList());
    }
}

