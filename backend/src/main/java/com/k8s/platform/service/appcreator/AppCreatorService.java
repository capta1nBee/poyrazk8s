package com.k8s.platform.service.appcreator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.dto.appcreator.*;
import com.k8s.platform.domain.entity.appcreator.AppCreatorApp;
import com.k8s.platform.domain.entity.appcreator.AppCreatorDraft;
import com.k8s.platform.repository.appcreator.AppCreatorAppRepository;
import com.k8s.platform.repository.appcreator.AppCreatorDraftRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppCreatorService {

    private final AppCreatorAppRepository appRepository;
    private final AppCreatorDraftRepository draftRepository;
    private final AppCreatorYamlGenerator yamlGenerator;
    private final ClusterContextManager clusterContextManager;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<AppCreatorAppDto> listApps(String clusterUid) {
        List<AppCreatorApp> apps = appRepository.findAllByClusterUidOrderByCreatedAtDesc(clusterUid);

        // For DEPLOYED apps, verify the main workload still exists in K8s
        KubernetesClient client = null;
        for (AppCreatorApp app : apps) {
            if ("DEPLOYED".equalsIgnoreCase(app.getStatus())) {
                try {
                    if (client == null) client = clusterContextManager.getClient(clusterUid);
                    boolean exists = checkWorkloadExists(client, app.getNamespace(), app.getWorkloadType(), app.getName());
                    if (!exists) {
                        log.warn("AppCreator app '{}' marked DEPLOYED but workload not found in K8s — updating status to NOT_FOUND", app.getName());
                        app.setStatus("NOT_FOUND");
                        appRepository.save(app);
                    }
                } catch (Exception e) {
                    log.warn("Could not verify K8s state for app '{}': {}", app.getName(), e.getMessage());
                }
            }
        }

        return apps.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppCreatorAppDto getApp(String clusterUid, UUID id) {
        return appRepository.findByIdAndClusterUid(id, clusterUid)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("App not found: " + id));
    }

    @Transactional
    public AppCreatorAppDto createApp(String clusterUid, AppCreatorCreateRequest req, Long userId) {
        AppCreatorApp app = AppCreatorApp.builder()
                .clusterUid(clusterUid)
                .name(req.getName())
                .description(req.getDescription())
                .namespace(req.getNamespace())
                .workloadType(req.getWorkloadType())
                .config(req.getConfig())
                .status("DRAFT")
                .templateId(req.getTemplateId())
                .createdBy(userId)
                .updatedBy(userId)
                .build();
        return toDto(appRepository.save(app));
    }

    @Transactional
    public AppCreatorAppDto updateApp(String clusterUid, UUID id, AppCreatorCreateRequest req, Long userId) {
        AppCreatorApp app = appRepository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("App not found: " + id));
        app.setName(req.getName());
        app.setDescription(req.getDescription());
        app.setNamespace(req.getNamespace());
        app.setWorkloadType(req.getWorkloadType());
        app.setConfig(req.getConfig());
        app.setUpdatedBy(userId);
        return toDto(appRepository.save(app));
    }

    @Transactional
    public void deleteApp(String clusterUid, UUID id, boolean deleteK8sResources) {
        AppCreatorApp app = appRepository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("App not found: " + id));
        if (deleteK8sResources) {
            deleteK8sResourcesForApp(clusterUid, app);
        }
        appRepository.delete(app);
    }

    public List<AppCreatorK8sResourceDto> listK8sResources(String clusterUid, UUID id) {
        AppCreatorApp app = appRepository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("App not found: " + id));
        List<AppCreatorK8sResourceDto> resources = new ArrayList<>();
        String namespace = app.getNamespace();
        String name = app.getName();
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        try {
            JsonNode cfg = objectMapper.readTree(app.getConfig());

            // Workload
            String kind = app.getWorkloadType();
            boolean workloadExists = checkWorkloadExists(client, namespace, kind, name);
            resources.add(new AppCreatorK8sResourceDto(kind, name, namespace, workloadExists));

            // Service
            if (getBooleanField(cfg, "createService")) {
                boolean exists = client.services().inNamespace(namespace).withName(name).get() != null;
                resources.add(new AppCreatorK8sResourceDto("Service", name, namespace, exists));
            }

            // Ingress
            if (getBooleanField(cfg, "createIngress")) {
                boolean exists = client.network().v1().ingresses().inNamespace(namespace).withName(name).get() != null;
                resources.add(new AppCreatorK8sResourceDto("Ingress", name, namespace, exists));
            }

            // HPA — generator names it "<name>-hpa"
            JsonNode hpa = cfg.get("hpa");
            if (hpa != null && hpa.path("enabled").asBoolean(false)) {
                String hpaName = name + "-hpa";
                boolean exists = client.autoscaling().v2().horizontalPodAutoscalers()
                        .inNamespace(namespace).withName(hpaName).get() != null;
                resources.add(new AppCreatorK8sResourceDto("HorizontalPodAutoscaler", hpaName, namespace, exists));
            }

            // ConfigMaps
            JsonNode configMaps = cfg.get("configMaps");
            if (configMaps != null && configMaps.isArray()) {
                for (JsonNode cm : configMaps) {
                    String cmName = cm.path("name").asText(null);
                    if (cmName != null && !cmName.isBlank()) {
                        boolean exists = client.configMaps().inNamespace(namespace).withName(cmName).get() != null;
                        resources.add(new AppCreatorK8sResourceDto("ConfigMap", cmName, namespace, exists));
                    }
                }
            }

            // Secrets
            JsonNode secrets = cfg.get("secrets");
            if (secrets != null && secrets.isArray()) {
                for (JsonNode sec : secrets) {
                    String secName = sec.path("name").asText(null);
                    if (secName != null && !secName.isBlank()) {
                        boolean exists = client.secrets().inNamespace(namespace).withName(secName).get() != null;
                        resources.add(new AppCreatorK8sResourceDto("Secret", secName, namespace, exists));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list K8s resources for app {}: {}", id, e.getMessage());
        }
        return resources;
    }

    private void deleteK8sResourcesForApp(String clusterUid, AppCreatorApp app) {
        String namespace = app.getNamespace();
        String name = app.getName();
        KubernetesClient client = clusterContextManager.getClient(clusterUid);
        try {
            JsonNode cfg = objectMapper.readTree(app.getConfig());

            // Workload
            deleteWorkload(client, namespace, app.getWorkloadType(), name);

            // HPA — generator names it "<name>-hpa"
            JsonNode hpa = cfg.get("hpa");
            if (hpa != null && hpa.path("enabled").asBoolean(false)) {
                String hpaName = name + "-hpa";
                safeDelete(() -> client.autoscaling().v2().horizontalPodAutoscalers()
                        .inNamespace(namespace).withName(hpaName).delete(), "HPA", hpaName);
            }

            // Service
            if (getBooleanField(cfg, "createService")) {
                safeDelete(() -> client.services().inNamespace(namespace).withName(name).delete(), "Service", name);
            }

            // Ingress
            if (getBooleanField(cfg, "createIngress")) {
                safeDelete(() -> client.network().v1().ingresses().inNamespace(namespace).withName(name).delete(), "Ingress", name);
            }

            // ConfigMaps
            JsonNode configMaps = cfg.get("configMaps");
            if (configMaps != null && configMaps.isArray()) {
                for (JsonNode cm : configMaps) {
                    String cmName = cm.path("name").asText(null);
                    if (cmName != null && !cmName.isBlank()) {
                        safeDelete(() -> client.configMaps().inNamespace(namespace).withName(cmName).delete(), "ConfigMap", cmName);
                    }
                }
            }

            // Secrets
            JsonNode secrets = cfg.get("secrets");
            if (secrets != null && secrets.isArray()) {
                for (JsonNode sec : secrets) {
                    String secName = sec.path("name").asText(null);
                    if (secName != null && !secName.isBlank()) {
                        safeDelete(() -> client.secrets().inNamespace(namespace).withName(secName).delete(), "Secret", secName);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error deleting K8s resources for app {}: {}", app.getId(), e.getMessage(), e);
        }
    }

    private boolean checkWorkloadExists(KubernetesClient client, String namespace, String kind, String name) {
        return switch (kind.toUpperCase()) {
            case "STATEFULSET" -> client.apps().statefulSets().inNamespace(namespace).withName(name).get() != null;
            case "DAEMONSET" -> client.apps().daemonSets().inNamespace(namespace).withName(name).get() != null;
            case "CRONJOB" -> client.batch().v1().cronjobs().inNamespace(namespace).withName(name).get() != null;
            default -> client.apps().deployments().inNamespace(namespace).withName(name).get() != null;
        };
    }

    private void deleteWorkload(KubernetesClient client, String namespace, String kind, String name) {
        switch (kind.toUpperCase()) {
            case "STATEFULSET" -> safeDelete(() -> client.apps().statefulSets().inNamespace(namespace).withName(name).delete(), "StatefulSet", name);
            case "DAEMONSET" -> safeDelete(() -> client.apps().daemonSets().inNamespace(namespace).withName(name).delete(), "DaemonSet", name);
            case "CRONJOB" -> safeDelete(() -> client.batch().v1().cronjobs().inNamespace(namespace).withName(name).delete(), "CronJob", name);
            default -> safeDelete(() -> client.apps().deployments().inNamespace(namespace).withName(name).delete(), "Deployment", name);
        }
    }

    private void safeDelete(Runnable action, String kind, String name) {
        try {
            action.run();
            log.info("Deleted K8s resource {}/{}", kind, name);
        } catch (Exception e) {
            log.warn("Could not delete K8s resource {}/{}: {}", kind, name, e.getMessage());
        }
    }

    private boolean getBooleanField(JsonNode node, String field) {
        return node != null && node.path(field).asBoolean(false);
    }

    @Transactional(readOnly = true)
    public AppCreatorYamlPreviewResponse previewYaml(String clusterUid, UUID id) {
        AppCreatorApp app = appRepository.findByIdAndClusterUid(id, clusterUid)
                .orElseThrow(() -> new IllegalArgumentException("App not found: " + id));
        Map<String, String> files = yamlGenerator.generate(app.getConfig());
        return AppCreatorYamlPreviewResponse.builder()
                .files(files)
                .resourceCount(files.size())
                .build();
    }

    @Transactional(readOnly = true)
    public AppCreatorYamlPreviewResponse previewYamlFromConfig(String configJson) {
        Map<String, String> files = yamlGenerator.generate(configJson);
        return AppCreatorYamlPreviewResponse.builder()
                .files(files)
                .resourceCount(files.size())
                .build();
    }

    // Draft management
    @Transactional
    public AppCreatorDraftDto saveDraft(String clusterUid, UUID draftId, String wizardState, int step, Long userId) {
        AppCreatorDraft draft;
        if (draftId != null) {
            draft = draftRepository.findByIdAndClusterUid(draftId, clusterUid)
                    .orElse(AppCreatorDraft.builder().clusterUid(clusterUid).createdBy(userId).build());
        } else {
            draft = AppCreatorDraft.builder().clusterUid(clusterUid).createdBy(userId).build();
        }
        draft.setWizardState(wizardState);
        draft.setCurrentStep(step);
        return toDraftDto(draftRepository.save(draft));
    }

    @Transactional(readOnly = true)
    public AppCreatorDraftDto getDraft(String clusterUid, UUID draftId) {
        return draftRepository.findByIdAndClusterUid(draftId, clusterUid)
                .map(this::toDraftDto)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));
    }

    @Transactional
    public void deleteDraft(String clusterUid, UUID draftId) {
        draftRepository.findByIdAndClusterUid(draftId, clusterUid)
                .ifPresent(draftRepository::delete);
    }

    private AppCreatorAppDto toDto(AppCreatorApp e) {
        return AppCreatorAppDto.builder()
                .id(e.getId()).clusterUid(e.getClusterUid()).name(e.getName())
                .description(e.getDescription()).namespace(e.getNamespace())
                .workloadType(e.getWorkloadType()).config(e.getConfig())
                .status(e.getStatus()).templateId(e.getTemplateId())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    private AppCreatorDraftDto toDraftDto(AppCreatorDraft e) {
        return AppCreatorDraftDto.builder()
                .id(e.getId()).clusterUid(e.getClusterUid()).appId(e.getAppId())
                .wizardState(e.getWizardState()).currentStep(e.getCurrentStep())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}

