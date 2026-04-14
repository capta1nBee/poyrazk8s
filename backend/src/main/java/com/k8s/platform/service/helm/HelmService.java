package com.k8s.platform.service.helm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class HelmService {

    private final ClusterRepository clusterRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<String> installChartAsync(String releaseName, String chartName, String namespace, String version, Map<String, Object> customValues, String customValuesYaml, String repoUrl, String username, String password, String clusterUid) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempKubeconfigPath = null;
            Path tempValuesFile = null;
            try {
                tempKubeconfigPath = prepareKubeconfig(clusterUid);

                // We use --repo argument in the main command instead of helm repo add if repository is provided
                String finalChartName = chartName;
                if (repoUrl != null && !repoUrl.trim().isEmpty() && chartName.contains("/")) {
                    // When using --repo, chart name must be just the name, not repo/name
                    finalChartName = chartName.split("/")[1];
                }

                // Check if already installed in ANY namespace to give meaningful error
                try {
                    String listOutput = runCommand(List.of("helm", "list", "--all-namespaces", "--filter", releaseName, "--kubeconfig", tempKubeconfigPath.toString()));
                    if (listOutput != null && listOutput.contains(releaseName)) {
                        String existingNs = extractNamespaceFromList(listOutput, releaseName);
                        throw new RuntimeException(
                            "Release '" + releaseName + "' already exists" +
                            (existingNs != null ? " in namespace '" + existingNs + "'" : "") +
                            ". Use upgrade if you want to update it."
                        );
                    }
                } catch (RuntimeException e) {
                    // Re-throw our meaningful error
                    if (e.getMessage() != null && e.getMessage().contains("already exists")) throw e;
                    // Otherwise ignore helm list errors
                }

                // Build install command
                List<String> command = new ArrayList<>(List.of(
                        "helm", "install", releaseName, finalChartName,
                        "--namespace", namespace,
                        "--create-namespace",
                        "--kubeconfig", tempKubeconfigPath.toString()
                ));

                if (version != null && !version.isEmpty()) {
                    command.add("--version");
                    command.add(version);
                }
                
                if (repoUrl != null && !repoUrl.trim().isEmpty()) {
                    command.add("--repo");
                    command.add(repoUrl);
                    if (username != null && !username.trim().isEmpty()) {
                        command.add("--username");
                        command.add(username);
                        if (password != null) {
                            command.add("--password");
                            command.add(password);
                        }
                    }
                }

                if (customValuesYaml != null && !customValuesYaml.trim().isEmpty()) {
                    tempValuesFile = Files.createTempFile("helm-values-raw-", ".yaml");
                    Files.writeString(tempValuesFile, customValuesYaml);
                    command.add("-f");
                    command.add(tempValuesFile.toAbsolutePath().toString());
                } else if (customValues != null && !customValues.isEmpty()) {
                    tempValuesFile = Files.createTempFile("helm-values-", ".yaml");
                    String yamlContent = objectMapper.writeValueAsString(customValues);
                    Files.writeString(tempValuesFile, yamlContent);
                    command.add("-f");
                    command.add(tempValuesFile.toAbsolutePath().toString());
                }

                return runCommand(command);
            } catch (Exception e) {
                log.error("Error executing helm install/deploy", e);
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                cleanup(tempKubeconfigPath);
                cleanup(tempValuesFile);
            }
        });
    }

    /**
     * List all helm releases for a cluster using its kubeconfig.
     */
    public CompletableFuture<String> listReleasesAsync(String clusterUid) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempKubeconfigPath = null;
            try {
                tempKubeconfigPath = prepareKubeconfig(clusterUid);
                return runCommand(List.of(
                        "helm", "list",
                        "--all-namespaces",
                        "--output", "json",
                        "--kubeconfig", tempKubeconfigPath.toString()
                ));
            } catch (Exception e) {
                log.error("Error executing helm list", e);
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                cleanup(tempKubeconfigPath);
            }
        });
    }

    /**
     * Get user-supplied values for a specific release.
     */
    public CompletableFuture<String> getReleaseValuesAsync(String releaseName, String namespace, String clusterUid) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempKubeconfigPath = null;
            try {
                tempKubeconfigPath = prepareKubeconfig(clusterUid);
                String output = runCommand(List.of(
                        "helm", "get", "values", releaseName,
                        "--namespace", namespace,
                        "--kubeconfig", tempKubeconfigPath.toString()
                ));
                if (output != null && output.startsWith("USER-SUPPLIED VALUES:")) {
                    output = output.replaceFirst("USER-SUPPLIED VALUES:\\r?\\n?", "").trim();
                }
                return output;
            } catch (Exception e) {
                log.error("Error executing helm get values for {}", releaseName, e);
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                cleanup(tempKubeconfigPath);
            }
        });
    }

    public CompletableFuture<String> upgradeReleaseAsync(String releaseName, String chartName, String namespace, String version, String customValuesYaml, String repoUrl, String username, String password, String clusterUid) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempKubeconfigPath = null;
            Path tempValuesFile = null;
            try {
                tempKubeconfigPath = prepareKubeconfig(clusterUid);
                String finalChartName = chartName;
                if (repoUrl != null && !repoUrl.trim().isEmpty() && chartName.contains("/")) {
                    finalChartName = chartName.split("/")[1];
                }
                
                List<String> command = new ArrayList<>(List.of(
                        "helm", "upgrade", releaseName, finalChartName,
                        "--namespace", namespace,
                        "--kubeconfig", tempKubeconfigPath.toString()
                ));
                if (version != null && !version.isEmpty()) {
                    command.add("--version");
                    command.add(version);
                }
                
                if (repoUrl != null && !repoUrl.trim().isEmpty()) {
                    command.add("--repo");
                    command.add(repoUrl);
                    if (username != null && !username.trim().isEmpty()) {
                        command.add("--username");
                        command.add(username);
                        if (password != null) {
                            command.add("--password");
                            command.add(password);
                        }
                    }
                }
                
                // Add new custom values
                if (customValuesYaml != null && !customValuesYaml.trim().isEmpty()) {
                    tempValuesFile = Files.createTempFile("helm-upgrade-values-", ".yaml");
                    Files.writeString(tempValuesFile, customValuesYaml);
                    command.add("-f");
                    command.add(tempValuesFile.toAbsolutePath().toString());
                }
                
                return runCommand(command);
            } catch (Exception e) {
                log.error("Error executing helm upgrade", e);
                throw new RuntimeException(e);
            } finally {
                cleanup(tempKubeconfigPath);
                cleanup(tempValuesFile);
            }
        });
    }

    public CompletableFuture<String> uninstallReleaseAsync(String releaseName, String namespace, String clusterUid) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempKubeconfigPath = null;
            try {
                tempKubeconfigPath = prepareKubeconfig(clusterUid);
                return runCommand(List.of(
                        "helm", "uninstall", releaseName,
                        "--namespace", namespace,
                        "--kubeconfig", tempKubeconfigPath.toString()
                ));
            } catch (Exception e) {
                log.error("Error executing helm uninstall", e);
                throw new RuntimeException(e);
            } finally {
                cleanup(tempKubeconfigPath);
            }
        });
    }

    public CompletableFuture<String> addRepositoryAsync(String name, String url, String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> command = new ArrayList<>(List.of("helm", "repo", "add", name, url, "--force-update"));
                if (username != null && !username.trim().isEmpty()) {
                    command.add("--username");
                    command.add(username);
                    if (password != null) {
                        command.add("--password");
                        command.add(password);
                    }
                }
                String result = runCommand(command);
                runCommand(List.of("helm", "repo", "update", name));
                return result;
            } catch (Exception e) {
                log.error("Error adding helm repository {}", name, e);
                throw new RuntimeException("Failed to add helm repository: " + e.getMessage());
            }
        });
    }

    public void updateRepositories() {
        try {
            runCommand(List.of("helm", "repo", "update"));
        } catch (Exception e) {
            log.warn("Failed to update helm repositories: {}", e.getMessage());
        }
    }

    private String extractNamespaceFromList(String listOutput, String releaseName) {
        for (String line : listOutput.split("\n")) {
            if (line.contains(releaseName)) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) return parts[1];
            }
        }
        return null;
    }

    private Path prepareKubeconfig(String clusterUid) throws IOException {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        String kubeconfigContent = cluster.getKubeconfig();
        if (kubeconfigContent == null || kubeconfigContent.isEmpty()) {
            throw new RuntimeException("Kubeconfig is empty for cluster: " + cluster.getName());
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(kubeconfigContent.trim());
            kubeconfigContent = new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Not base64, use as is
        }

        Path tempFile = Files.createTempFile("kubeconfig-", ".yaml");
        Files.writeString(tempFile, kubeconfigContent);
        // Restrict permissions to owner-only (chmod 600) to avoid helm security warning
        tempFile.toFile().setReadable(false, false);
        tempFile.toFile().setReadable(true, true);
        tempFile.toFile().setWritable(false, false);
        tempFile.toFile().setWritable(true, true);
        return tempFile;
    }

    private void cleanup(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Failed to delete temporary file {}: {}", path, e.getMessage());
            }
        }
    }

    private String runCommand(List<String> command) throws Exception {
        log.info("Executing: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        String result = output.toString();

        if (exitCode != 0) {
            log.error("Command failed with exit code {}. Output: {}", exitCode, result);
            throw new RuntimeException("Command failed: " + result);
        }

        return result;
    }
}
