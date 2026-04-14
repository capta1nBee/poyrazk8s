package com.k8s.platform.service.appcreator;

import com.k8s.platform.domain.dto.appcreator.GitBranchDto;
import com.k8s.platform.domain.dto.appcreator.GitConnectionDto;
import com.k8s.platform.domain.dto.appcreator.GitConnectionCreateRequest;
import com.k8s.platform.domain.dto.appcreator.GitRepoDto;
import com.k8s.platform.domain.entity.appcreator.GitConnection;
import com.k8s.platform.repository.appcreator.GitConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitService {

    private final GitConnectionRepository connectionRepository;
    private final RestTemplate restTemplate;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public GitConnectionDto create(String clusterUid, Long userId, GitConnectionCreateRequest req) {
        GitConnection conn = GitConnection.builder()
                .clusterUid(clusterUid)
                .userId(userId)
                .provider(req.getProvider())
                .name(req.getName())
                .accessToken(req.getAccessToken())
                .baseUrl(normalizeBaseUrl(req.getProvider(), req.getBaseUrl()))
                .isDefault(Boolean.TRUE.equals(req.getIsDefault()))
                .build();
        return toDto(connectionRepository.save(conn));
    }

    /** List ALL connections for a cluster — all authorized users see shared connections */
    public List<GitConnectionDto> listForCluster(String clusterUid, Long userId) {
        return connectionRepository.findAllByClusterUidOrderByCreatedAtDesc(clusterUid)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public GitConnectionDto update(String clusterUid, UUID id, Long userId, GitConnectionCreateRequest req) {
        GitConnection conn = connectionRepository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("Git connection not found: " + id));
        conn.setProvider(req.getProvider());
        conn.setName(req.getName());
        if (req.getAccessToken() != null && !req.getAccessToken().isBlank()) {
            conn.setAccessToken(req.getAccessToken());
        }
        conn.setBaseUrl(normalizeBaseUrl(req.getProvider(), req.getBaseUrl()));
        conn.setIsDefault(Boolean.TRUE.equals(req.getIsDefault()));
        return toDto(connectionRepository.save(conn));
    }

    public void delete(String clusterUid, UUID id, Long userId) {
        GitConnection conn = connectionRepository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("Git connection not found: " + id));
        connectionRepository.delete(conn);
    }

    // ── Repo listing ──────────────────────────────────────────────────────────

    public List<GitRepoDto> listRepositories(String clusterUid, UUID connectionId, Long userId) {
        GitConnection conn = getConn(clusterUid, connectionId, userId);
        if ("github".equals(conn.getProvider())) {
            return githubListRepos(conn);
        } else {
            return gitlabListRepos(conn);
        }
    }

    // ── Branch listing ────────────────────────────────────────────────────────

    public List<GitBranchDto> listBranches(String clusterUid, UUID connectionId, Long userId, String owner, String repo) {
        GitConnection conn = getConn(clusterUid, connectionId, userId);
        if ("github".equals(conn.getProvider())) {
            return githubListBranches(conn, owner, repo);
        } else {
            return gitlabListBranches(conn, owner + "/" + repo);
        }
    }

    // ── Push YAML + create PR ─────────────────────────────────────────────────

    /**
     * Commits YAML files to a new branch derived from baseBranch and opens a PR/MR.
     *
     * @return the URL of the created PR / MR
     */
    public String pushYamlAndCreatePR(GitConnection conn, String owner, String repo,
                                      String baseBranch, String appName,
                                      Map<String, String> yamlFiles, String gitPath) {
        if ("github".equals(conn.getProvider())) {
            return githubPushAndPR(conn, owner, repo, baseBranch, appName, yamlFiles, gitPath);
        } else {
            return gitlabPushAndMR(conn, owner + "/" + repo, baseBranch, appName, yamlFiles, gitPath);
        }
    }

    public GitConnection getConn(String clusterUid, UUID id, Long userId) {
        return connectionRepository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("Git connection not found: " + id));
    }

    // ── GitHub implementation ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<GitRepoDto> githubListRepos(GitConnection conn) {
        String url = githubBase(conn) + "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator";
        List<Map<String, Object>> raw = getList(url, conn.getAccessToken());
        return raw.stream().map(r -> GitRepoDto.builder()
                .fullName((String) r.get("full_name"))
                .name((String) r.get("name"))
                .description((String) r.get("description"))
                .defaultBranch((String) r.get("default_branch"))
                .isPrivate(Boolean.TRUE.equals(r.get("private")))
                .htmlUrl((String) r.get("html_url"))
                .build()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<GitBranchDto> githubListBranches(GitConnection conn, String owner, String repo) {
        String url = githubBase(conn) + "/repos/" + owner + "/" + repo + "/branches?per_page=100";
        List<Map<String, Object>> raw = getList(url, conn.getAccessToken());
        return raw.stream().map(b -> GitBranchDto.builder()
                .name((String) b.get("name"))
                .commitSha((String) ((Map<?, ?>) b.get("commit")).get("sha"))
                .isProtected(Boolean.TRUE.equals(b.get("protected")))
                .build()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private String githubPushAndPR(GitConnection conn, String owner, String repo,
                                   String baseBranch, String appName,
                                   Map<String, String> yamlFiles, String gitPath) {
        String base = githubBase(conn);
        String token = conn.getAccessToken();

        // 1. Get base branch SHA
        String refUrl = base + "/repos/" + owner + "/" + repo + "/git/ref/heads/" + baseBranch;
        Map<String, Object> refData = getOne(refUrl, token);
        String baseSha = (String) ((Map<?, ?>) ((Map<?, ?>) refData.get("object"))).get("sha");

        // 2. Create feature branch
        String featureBranch = "appcreator/" + appName.toLowerCase().replaceAll("[^a-z0-9]", "-")
                + "-" + System.currentTimeMillis();
        postJson(base + "/repos/" + owner + "/" + repo + "/git/refs", token,
                Map.of("ref", "refs/heads/" + featureBranch, "sha", baseSha));

        // 3. Commit each YAML file
        String path = (gitPath != null && !gitPath.isBlank()) ? gitPath : "k8s";
        for (Map.Entry<String, String> entry : yamlFiles.entrySet()) {
            String filePath = path + "/" + appName + "/" + entry.getKey();
            String encoded = Base64.getEncoder().encodeToString(entry.getValue().getBytes());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "chore(appcreator): add " + entry.getKey() + " for " + appName);
            body.put("content", encoded);
            body.put("branch", featureBranch);
            putJson(base + "/repos/" + owner + "/" + repo + "/contents/" + filePath, token, body);
        }

        // 4. Create PR
        Map<String, Object> prBody = Map.of(
                "title", "[AppCreator] Deploy " + appName,
                "head", featureBranch,
                "base", baseBranch,
                "body", "Auto-generated by PoyrazK8s AppCreator");
        Map<String, Object> pr = postJson(base + "/repos/" + owner + "/" + repo + "/pulls", token, prBody);
        return (String) pr.get("html_url");
    }

    // ── GitLab implementation ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<GitRepoDto> gitlabListRepos(GitConnection conn) {
        String url = gitlabBase(conn) + "/projects?membership=true&per_page=100&order_by=last_activity_at";
        List<Map<String, Object>> raw = getList(url, conn.getAccessToken());
        return raw.stream().map(r -> {
            Map<String, Object> ns = (Map<String, Object>) r.getOrDefault("namespace", Map.of());
            return GitRepoDto.builder()
                    .fullName((String) r.get("path_with_namespace"))
                    .name((String) r.get("name"))
                    .description((String) r.get("description"))
                    .defaultBranch((String) r.get("default_branch"))
                    .isPrivate(!"public".equals(r.get("visibility")))
                    .htmlUrl((String) r.get("web_url"))
                    .build();
        }).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<GitBranchDto> gitlabListBranches(GitConnection conn, String projectPath) {
        String encoded = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        String url = gitlabBase(conn) + "/projects/" + encoded + "/repository/branches?per_page=100";
        List<Map<String, Object>> raw = getList(url, conn.getAccessToken());
        return raw.stream().map(b -> GitBranchDto.builder()
                .name((String) b.get("name"))
                .commitSha((String) ((Map<?, ?>) b.get("commit")).get("id"))
                .isProtected(Boolean.TRUE.equals(b.get("protected")))
                .build()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private String gitlabPushAndMR(GitConnection conn, String projectPath,
                                   String baseBranch, String appName,
                                   Map<String, String> yamlFiles, String gitPath) {
        String base = gitlabBase(conn);
        String token = conn.getAccessToken();
        String encoded = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        String featureBranch = "appcreator/" + appName.toLowerCase().replaceAll("[^a-z0-9]", "-")
                + "-" + System.currentTimeMillis();
        String path = (gitPath != null && !gitPath.isBlank()) ? gitPath : "k8s";

        // Build commits array for GitLab's commits API
        List<Map<String, Object>> actions = new ArrayList<>();
        for (Map.Entry<String, String> entry : yamlFiles.entrySet()) {
            actions.add(Map.of(
                    "action", "create",
                    "file_path", path + "/" + appName + "/" + entry.getKey(),
                    "content", entry.getValue()
            ));
        }

        Map<String, Object> commitBody = new LinkedHashMap<>();
        commitBody.put("branch", featureBranch);
        commitBody.put("start_branch", baseBranch);
        commitBody.put("commit_message", "[AppCreator] Deploy " + appName);
        commitBody.put("actions", actions);
        postJson(base + "/projects/" + encoded + "/repository/commits", token, commitBody);

        // Create MR
        Map<String, Object> mrBody = Map.of(
                "source_branch", featureBranch,
                "target_branch", baseBranch,
                "title", "[AppCreator] Deploy " + appName,
                "description", "Auto-generated by PoyrazK8s AppCreator",
                "remove_source_branch", true
        );
        Map<String, Object> mr = postJson(base + "/projects/" + encoded + "/merge_requests", token, mrBody);
        return (String) mr.get("web_url");
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(String url, String token) {
        // URI.create() preserves pre-encoded sequences like %2F so RestTemplate
        // does not double-encode them (avoids GitLab 404 Project Not Found).
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                URI.create(url), HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {});
        return resp.getBody() != null ? resp.getBody() : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOne(String url, String token) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                URI.create(url), HttpMethod.GET, new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<>() {});
        return resp.getBody() != null ? resp.getBody() : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJson(String url, String token, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                URI.create(url), HttpMethod.POST, new HttpEntity<>(body, headers(token)),
                new ParameterizedTypeReference<>() {});
        return resp.getBody() != null ? resp.getBody() : Map.of();
    }

    @SuppressWarnings("unchecked")
    private void putJson(String url, String token, Map<String, Object> body) {
        restTemplate.exchange(URI.create(url), HttpMethod.PUT, new HttpEntity<>(body, headers(token)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private String githubBase(GitConnection c) {
        return c.getBaseUrl() != null ? c.getBaseUrl() : "https://api.github.com";
    }

    private String gitlabBase(GitConnection c) {
        return c.getBaseUrl() != null ? c.getBaseUrl() : "https://gitlab.com/api/v4";
    }

    private String normalizeBaseUrl(String provider, String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.stripTrailing();
        if ("gitlab".equals(provider) && !trimmed.endsWith("/api/v4")) {
            trimmed = trimmed.replaceAll("/+$", "") + "/api/v4";
        }
        return trimmed;
    }

    private GitConnectionDto toDto(GitConnection c) {
        return GitConnectionDto.builder()
                .id(c.getId())
                .provider(c.getProvider())
                .name(c.getName())
                .baseUrl(c.getBaseUrl())
                .isDefault(c.getIsDefault())
                .createdAt(c.getCreatedAt())
                .build();
    }
}

