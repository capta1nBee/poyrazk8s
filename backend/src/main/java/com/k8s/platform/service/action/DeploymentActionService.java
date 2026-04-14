package com.k8s.platform.service.action;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentActionService {
    private final ClusterContextManager clusterContextManager;

    public void scale(Long clusterId, String namespace, String deploymentName, Integer replicas) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            client.apps().deployments().inNamespace(namespace).withName(deploymentName).scale(replicas);
            log.info("Scaled deployment {}/{} to {} replicas", namespace, deploymentName, replicas);
        } catch (Exception e) {
            log.error("Failed to scale deployment: {}/{}", namespace, deploymentName, e);
            throw new RuntimeException("Failed to scale deployment: " + e.getMessage(), e);
        }
    }

    public void rolloutRestart(Long clusterId, String namespace, String deploymentName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
            if (deployment == null) throw new RuntimeException("Deployment not found: " + deploymentName);
            Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();
            if (annotations == null) annotations = new HashMap<>();
            annotations.put("kubectl.kubernetes.io/restartedAt", OffsetDateTime.now().toString());
            deployment.getSpec().getTemplate().getMetadata().setAnnotations(annotations);
            client.apps().deployments().inNamespace(namespace).withName(deploymentName).replace(deployment);
            log.info("Rollout restart initiated for deployment: {}/{}", namespace, deploymentName);
        } catch (Exception e) {
            log.error("Failed to restart deployment: {}/{}", namespace, deploymentName, e);
            throw new RuntimeException("Failed to restart deployment: " + e.getMessage(), e);
        }
    }

    public void pause(Long clusterId, String namespace, String deploymentName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
            if (deployment == null) throw new RuntimeException("Deployment not found: " + deploymentName);
            deployment.getSpec().setPaused(true);
            client.apps().deployments().inNamespace(namespace).withName(deploymentName).replace(deployment);
            log.info("Paused deployment: {}/{}", namespace, deploymentName);
        } catch (Exception e) {
            log.error("Failed to pause deployment: {}/{}", namespace, deploymentName, e);
            throw new RuntimeException("Failed to pause deployment: " + e.getMessage(), e);
        }
    }

    public void resume(Long clusterId, String namespace, String deploymentName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
            if (deployment == null) throw new RuntimeException("Deployment not found: " + deploymentName);
            deployment.getSpec().setPaused(false);
            client.apps().deployments().inNamespace(namespace).withName(deploymentName).replace(deployment);
            log.info("Resumed deployment: {}/{}", namespace, deploymentName);
        } catch (Exception e) {
            log.error("Failed to resume deployment: {}/{}", namespace, deploymentName, e);
            throw new RuntimeException("Failed to resume deployment: " + e.getMessage(), e);
        }
    }

    public void rollback(Long clusterId, String namespace, String deploymentName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            client.apps().deployments().inNamespace(namespace).withName(deploymentName).rolling().undo();
            log.info("Rollback initiated for deployment: {}/{}", namespace, deploymentName);
        } catch (Exception e) {
            log.error("Failed to rollback deployment: {}/{}", namespace, deploymentName, e);
            throw new RuntimeException("Failed to rollback deployment: " + e.getMessage(), e);
        }
    }
}
