package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.StatefulSet;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.StatefulSetRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatefulSetService {

        private final StatefulSetRepository statefulSetRepository;
        private final ClusterRepository clusterRepository;
        private final ClusterContextManager clusterContextManager;

        public List<StatefulSet> listStatefulSets(String clusterUid) {
                return listStatefulSets(clusterUid, false);
        }

        public List<StatefulSet> listStatefulSets(String clusterUid, boolean includeDeleted) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                if (includeDeleted) {
                        return statefulSetRepository.findByClusterId(cluster.getId());
                }
                return statefulSetRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
        }

        public List<StatefulSet> listStatefulSets(String clusterUid, String namespace) {
                return listStatefulSets(clusterUid, namespace, false);
        }

        public List<StatefulSet> listStatefulSets(String clusterUid, String namespace, boolean includeDeleted) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                if (includeDeleted) {
                        return statefulSetRepository.findByClusterIdAndNamespace(cluster.getId(), namespace);
                }
                return statefulSetRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
        }

        public StatefulSet getStatefulSet(String clusterUid, String namespace, String name) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                return statefulSetRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(
                                cluster.getId(), namespace, name)
                                .orElseThrow(() -> new RuntimeException("StatefulSet not found: " + name));
        }

        public void scaleStatefulSet(String clusterUid, String namespace, String name, int replicas) {
                log.info("Scaling StatefulSet: {}/{} to {} replicas in cluster UID: {}",
                                namespace, name, replicas, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().statefulSets()
                                .inNamespace(namespace)
                                .withName(name)
                                .scale(replicas);

                log.info("StatefulSet scaled successfully: {}/{} to {} replicas", namespace, name, replicas);
        }

        public void restartStatefulSet(String clusterUid, String namespace, String name) {
                log.info("Restarting StatefulSet: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().statefulSets()
                                .inNamespace(namespace)
                                .withName(name)
                                .rolling()
                                .restart();

                log.info("StatefulSet restarted successfully: {}/{}", namespace, name);
        }

        public void deleteStatefulSet(String clusterUid, String namespace, String name) {
                log.info("Deleting StatefulSet: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.apps().statefulSets()
                                .inNamespace(namespace)
                                .withName(name)
                                .delete();

                log.info("StatefulSet deleted successfully: {}/{}", namespace, name);
        }

        public void deletePodByOrdinal(String clusterUid, String namespace, String statefulSetName, int ordinal) {
                log.info("Deleting pod ordinal {} from StatefulSet: {}/{} in cluster UID: {}",
                                ordinal, namespace, statefulSetName, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                String podName = statefulSetName + "-" + ordinal;

                client.pods()
                                .inNamespace(namespace)
                                .withName(podName)
                                .delete();

                log.info("Pod deleted successfully: {}/{}", namespace, podName);
        }
}
