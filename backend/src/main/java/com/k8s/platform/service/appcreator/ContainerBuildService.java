package com.k8s.platform.service.appcreator;

import com.k8s.platform.domain.dto.appcreator.ContainerBuildJobDto;
import com.k8s.platform.domain.dto.appcreator.ContainerBuildRequest;
import com.k8s.platform.domain.entity.appcreator.GitConnection;
import com.k8s.platform.domain.entity.appcreator.RegistryConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerBuildService {

    private final GitService gitService;
    private final RegistryConnectionService registryService;

    /** In-memory job store (sufficient for MVP; replace with DB if needed) */
    private final Map<String, ContainerBuildJobDto> jobs = new ConcurrentHashMap<>();

    /**
     * DOCKER_HOST resolved once at class-load time.
     * Explicit injection into ProcessBuilder ensures the Docker CLI always talks to
     * the DinD daemon (tcp://dind:2375) regardless of how the JVM inherited its
     * env.
     */
    private static final String DOCKER_HOST = System.getenv("DOCKER_HOST");

    // ── Public API ────────────────────────────────────────────────────────────

    public ContainerBuildJobDto startBuild(String clusterUid, Long userId, ContainerBuildRequest req) {
        String jobId = UUID.randomUUID().toString();
        ContainerBuildJobDto job = ContainerBuildJobDto.builder()
                .jobId(jobId)
                .status("PENDING")
                .logs("")
                .build();
        jobs.put(jobId, job);
        runBuild(clusterUid, userId, req, jobId);
        return job;
    }

    public ContainerBuildJobDto getJob(String jobId) {
        return jobs.getOrDefault(jobId,
                ContainerBuildJobDto.builder().jobId(jobId).status("NOT_FOUND").build());
    }

    // ── Async build execution ─────────────────────────────────────────────────

    @Async
    protected void runBuild(String clusterUid, Long userId, ContainerBuildRequest req, String jobId) {
        updateStatus(jobId, "RUNNING", null, null);
        Path workDir = null;
        StringBuilder logs = new StringBuilder();

        try {
            GitConnection gitConn = gitService.getConn(
                    clusterUid, UUID.fromString(req.getGitConnectionId()), userId);
            RegistryConnection regConn = registryService.getConn(
                    clusterUid, UUID.fromString(req.getRegistryConnectionId()), userId);

            // 1. Prepare temp directory
            workDir = Files.createTempDirectory("appcreator-build-");

            // 2. Clone repository
            String cloneUrl = buildCloneUrl(gitConn, req.getRepoPath());
            logs.append(runCmd(workDir.toFile(), logs,
                    "git", "clone", "--depth=1", "--branch", req.getBranch(), cloneUrl, "repo"));

            // 3. Determine image tag
            String tag = req.getAppName().toLowerCase().replaceAll("[^a-z0-9]", "-")
                    + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
            String imageRef = buildImageRef(regConn, tag, req.getRepoPath());

            // 4. Docker build
            File repoDir = workDir.resolve("repo").toFile();
            String dockerfile = (req.getDockerfilePath() == null || req.getDockerfilePath().isBlank())
                    ? "Dockerfile"
                    : req.getDockerfilePath();
            logs.append(runCmd(repoDir, logs,
                    "docker", "build", "-t", imageRef, "-f", dockerfile, "."));

            // 5. Docker login
            String server = regConn.getServerUrl() != null ? regConn.getServerUrl() : "";
            logs.append(runCmdWithStdin(repoDir, regConn.getPasswordToken(),
                    "docker", "login", "--username", regConn.getUsername(), "--password-stdin", server));

            // 6. Docker push
            logs.append(runCmd(repoDir, logs, "docker", "push", imageRef));

            updateJob(jobId, "SUCCESS", imageRef, logs.toString(), null);
            log.info("[BUILD] {} → {} SUCCESS", jobId, imageRef);

        } catch (Exception e) {
            log.error("[BUILD] {} FAILED: {}", jobId, e.getMessage(), e);
            updateJob(jobId, "FAILED", null, logs.toString(), e.getMessage());
        } finally {
            // 7. Cleanup temp directory
            if (workDir != null)
                deleteDir(workDir.toFile());
        }
    }

    // ── Public docker login ───────────────────────────────────────────────────

    /**
     * Performs docker login for the given registry connection.
     * Safe to call proactively (on create) and reactively (on startup).
     */
    public void loginRegistry(RegistryConnection conn) {
        String server = conn.getServerUrl() != null ? conn.getServerUrl() : "";
        try {
            List<String> cmd = new ArrayList<>(List.of(
                    "docker", "login", "--username", conn.getUsername(), "--password-stdin"));
            if (!server.isBlank())
                cmd.add(server);
            ProcessBuilder pb = createPb(null, cmd);
            Process proc = pb.start();
            proc.getOutputStream().write(conn.getPasswordToken().getBytes());
            proc.getOutputStream().close();
            String out = new String(proc.getInputStream().readAllBytes());
            int code = proc.waitFor();
            if (code != 0) {
                log.warn("[DOCKER-LOGIN] Failed for registry '{}' ({}): {}", conn.getName(), server, out);
            } else {
                log.info("[DOCKER-LOGIN] Success for registry '{}' ({})", conn.getName(), server);
            }
        } catch (Exception e) {
            log.warn("[DOCKER-LOGIN] Error logging in to registry '{}': {}", conn.getName(), e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildCloneUrl(GitConnection conn, String repoPath) {
        String token = conn.getAccessToken();
        if ("github".equals(conn.getProvider())) {
            return "https://" + token + "@github.com/" + repoPath + ".git";
        } else {
            // baseUrl is stored as "https://host/api/v4" for GitLab REST API calls.
            // For git clone we need only the host — strip the /api/v4 (or any trailing
            // path).
            String base = conn.getBaseUrl() != null ? conn.getBaseUrl() : "https://gitlab.com";
            // Remove /api/v4 suffix (and any trailing slashes) to get the plain host URL
            base = base.replaceAll("/api/v4/?$", "").replaceAll("/+$", "");
            // Strip protocol so we can inject credentials
            base = base.replace("https://", "").replace("http://", "");
            return "https://oauth2:" + token + "@" + base + "/" + repoPath + ".git";
        }
    }

    private String buildImageRef(RegistryConnection reg, String tag, String repoPath) {
        String server = reg.getServerUrl() != null ? reg.getServerUrl() : "";
        String prefix;
        if (reg.getImagePrefix() != null && !reg.getImagePrefix().isBlank()) {
            prefix = reg.getImagePrefix();
        } else if (repoPath != null && !repoPath.isBlank()) {
            prefix = repoPath;
        } else {
            prefix = reg.getUsername();
        }
        String ref = !server.isBlank()
                ? server + "/" + prefix + "/" + tag
                : prefix + "/" + tag;
        return ref.toLowerCase();
    }

    /**
     * Creates a ProcessBuilder with DOCKER_HOST explicitly set so the Docker CLI
     * always uses DinD.
     */
    private ProcessBuilder createPb(File dir, List<String> cmd) {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (dir != null)
            pb.directory(dir);
        if (DOCKER_HOST != null && !DOCKER_HOST.isBlank()) {
            pb.environment().put("DOCKER_HOST", DOCKER_HOST);
            log.debug("[DOCKER] Using DOCKER_HOST={}", DOCKER_HOST);
        }
        return pb;
    }

    private String runCmd(File dir, StringBuilder logs, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = createPb(dir, Arrays.asList(cmd));
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes());
        int code = proc.waitFor();
        if (code != 0)
            throw new RuntimeException("Command failed (exit " + code + "): " + String.join(" ", cmd) + "\n" + out);
        return out + "\n";
    }

    private String runCmdWithStdin(File dir, String stdin, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = createPb(dir, Arrays.asList(cmd));
        Process proc = pb.start();
        proc.getOutputStream().write(stdin.getBytes());
        proc.getOutputStream().close();
        String out = new String(proc.getInputStream().readAllBytes());
        int code = proc.waitFor();
        if (code != 0)
            throw new RuntimeException("Command failed (exit " + code + "): " + String.join(" ", cmd) + "\n" + out);
        return out + "\n";
    }

    private void updateStatus(String jobId, String status, String imageRef, String error) {
        jobs.computeIfPresent(jobId, (k, j) -> {
            j.setStatus(status);
            if (imageRef != null)
                j.setImageRef(imageRef);
            if (error != null)
                j.setErrorMessage(error);
            return j;
        });
    }

    private void updateJob(String jobId, String status, String imageRef, String logs, String error) {
        jobs.computeIfPresent(jobId, (k, j) -> {
            j.setStatus(status);
            j.setImageRef(imageRef);
            j.setLogs(logs);
            j.setErrorMessage(error);
            return j;
        });
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists())
            return;
        try {
            Files.walk(dir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(f -> {
                        if (!f.delete())
                            log.warn("[BUILD] Could not delete: {}", f.getAbsolutePath());
                    });
        } catch (IOException e) {
            log.warn("[BUILD] Failed to clean up temp dir {}: {}", dir.getAbsolutePath(), e.getMessage());
        }
    }
}
