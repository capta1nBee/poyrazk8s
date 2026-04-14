package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.DaemonSet;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.DaemonSetRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DaemonSetService {

        private final DaemonSetRepository daemonSetRepository;
        private final ClusterRepository clusterRepository;
        private final ClusterContextManager clusterContextManager;

        public List<DaemonSet> listDaemonSets(String clusterUid) {
                return listDaemonSets(clusterUid, false);
        }

        public List<DaemonSet> listDaemonSets(String clusterUid, boolean includeDeleted) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                if (includeDeleted) {
                        return daemonSetRepository.findByClusterId(cluster.getId());
                }
                return daemonSetRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
        }

        public List<DaemonSet> listDaemonSets(String clusterUid, String namespace) {
                return listDaemonSets(clusterUid, namespace, false);
        }

        public List<DaemonSet> listDaemonSets(String clusterUid, String namespace, boolean includeDeleted) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                if (includeDeleted) {
                        return daemonSetRepository.findByClusterIdAndNamespace(cluster.getId(), namespace);
                }
                return daemonSetRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
        }

        public DaemonSet getDaemonSet(String clusterUid, String namespace, String name) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                return daemonSetRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(
                                cluster.getId(), namespace, name)
                                .orElseThrow(() -> new RuntimeException("DaemonSet not found: " + name));
        }

        public void restartDaemonSet(String clusterUid, String namespace, String name) {
                log.info("Restarting DaemonSet: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Trigger restart by updating a dummy annotation
                client.apps().daemonSets()
                                .inNamespace(namespace)
                                .withName(name)
                                .edit(ds -> new io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder(ds)
                                                .editMetadata()
                                                .addToAnnotations("kubectl.kubernetes.io/restartedAt",
                                                                String.valueOf(System.currentTimeMillis()))
                                                .endMetadata()
                                                .build());

                log.info("DaemonSet restarted successfully: {}/{}", namespace, name);
        }

        public void pauseDaemonSet(String clusterUid, String namespace, String name) {
                log.info("Pausing DaemonSet: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Pause by scaling to 0 is not supported for DaemonSet
                // Instead, we can add a node selector that matches no nodes
                client.apps().daemonSets()
                                .inNamespace(namespace)
                                .withName(name)
                                .edit(ds -> new io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder(ds)
                                                .editSpec()
                                                .editTemplate()
                                                .editSpec()
                                                .addToNodeSelector("paused-by-platform", "true")
                                                .endSpec()
                                                .endTemplate()
                                                .endSpec()
                                                .build());

                log.info("DaemonSet paused successfully: {}/{}", namespace, name);
        }

        public void resumeDaemonSet(String clusterUid, String namespace, String name) {
                log.info("Resuming DaemonSet: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                // Resume by removing the pause node selector
                client.apps().daemonSets()
                                .inNamespace(namespace)
                                .withName(name)
                                .edit(ds -> new io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder(ds)
                                                .editSpec()
                                                .editTemplate()
                                                .editSpec()
                                                .removeFromNodeSelector("paused-by-platform")
                                                .endSpec()
                                                .endTemplate()
                                                .endSpec()
                                                .build());

                log.info("DaemonSet resumed successfully: {}/{}", namespace, name);
        }

        public void deleteDaemonSet(String clusterUid, String namespace, String name) {
                log.info("Deleting DaemonSet: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().daemonSets()
                                .inNamespace(namespace)
                                .withName(name)
                                .delete();

                log.info("DaemonSet deleted successfully: {}/{}", namespace, name);
        }
}
