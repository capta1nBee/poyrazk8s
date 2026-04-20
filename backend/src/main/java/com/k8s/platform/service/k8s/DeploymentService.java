package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.Deployment;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.DeploymentRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentService {

        private final DeploymentRepository deploymentRepository;
        private final ClusterRepository clusterRepository;
        private final ClusterContextManager clusterContextManager;

        public List<Deployment> listDeployments(String clusterUid) {
                return listDeployments(clusterUid, false);
        }

        public List<Deployment> listDeployments(String clusterUid, boolean includeDeleted) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                if (includeDeleted) {
                        return deploymentRepository.findByClusterId(cluster.getId());
                }
                return deploymentRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
        }

        public List<Deployment> listDeployments(String clusterUid, String namespace) {
                return listDeployments(clusterUid, namespace, false);
        }

        public List<Deployment> listDeployments(String clusterUid, String namespace, boolean includeDeleted) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                if (includeDeleted) {
                        return deploymentRepository.findByClusterIdAndNamespace(cluster.getId(), namespace);
                }
                return deploymentRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
        }

        public Deployment getDeployment(String clusterUid, String namespace, String name) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                return deploymentRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(
                                cluster.getId(), namespace, name)
                                .orElseThrow(() -> new RuntimeException("Deployment not found: " + name));
        }

        public void scaleDeployment(String clusterUid, String namespace, String name, int replicas) {
                log.info("Scaling deployment: {}/{} to {} replicas in cluster UID: {}",
                                namespace, name, replicas, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .scale(replicas);

                // Immediately update DB so the UI reflects the new replica count
                // before the watcher MODIFIED event arrives.
                try {
                        Cluster cluster = clusterRepository.findByUid(clusterUid)
                                        .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));
                        deploymentRepository
                                        .findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(cluster.getId(), namespace,
                                                        name)
                                        .ifPresent(d -> {
                                                d.setReplicas(replicas);
                                                deploymentRepository.save(d);
                                        });
                } catch (Exception e) {
                        log.warn("Could not eagerly update deployment replica count in DB: {}", e.getMessage());
                }

                log.info("Deployment scaled successfully: {}/{} to {} replicas", namespace, name, replicas);
        }

        public void restartDeployment(String clusterUid, String namespace, String name) {
                log.info("Restarting deployment: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Trigger rollout restart by adding annotation
                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .rolling()
                                .restart();

                log.info("Deployment restarted successfully: {}/{}", namespace, name);
        }

        public void deleteDeployment(String clusterUid, String namespace, String name) {
                log.info("Deleting deployment: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .delete();

                log.info("Deployment deleted successfully: {}/{}", namespace, name);
        }

        public void pauseDeployment(String clusterUid, String namespace, String name) {
                log.info("Pausing deployment: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .rolling()
                                .pause();

                log.info("Deployment paused successfully: {}/{}", namespace, name);
        }

        public void resumeDeployment(String clusterUid, String namespace, String name) {
                log.info("Resuming deployment: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .rolling()
                                .resume();

                log.info("Deployment resumed successfully: {}/{}", namespace, name);
        }

        public void undoRollout(String clusterUid, String namespace, String name) {
                log.info("Rolling back deployment: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().deployments()
                                .inNamespace(namespace)
                                .withName(name)
                                .rolling()
                                .undo();

                log.info("Deployment rolled back successfully: {}/{}", namespace, name);
        }
}
