package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.Job;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.JobRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

        private final JobRepository jobRepository;
        private final ClusterRepository clusterRepository;
        private final ClusterContextManager clusterContextManager;

        public List<Job> listJobs(String clusterUid) {
                return listJobs(clusterUid, false);
        }

        public List<Job> listJobs(String clusterUid, boolean includeDeleted) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                if (includeDeleted) {
                        return jobRepository.findByClusterId(cluster.getId());
                }
                return jobRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
        }

        public List<Job> listJobs(String clusterUid, String namespace) {
                return listJobs(clusterUid, namespace, false);
        }

        public List<Job> listJobs(String clusterUid, String namespace, boolean includeDeleted) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                if (includeDeleted) {
                        return jobRepository.findByClusterIdAndNamespace(cluster.getId(), namespace);
                }
                return jobRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
        }

        public Job getJob(String clusterUid, String namespace, String name) {
                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                return jobRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(
                                cluster.getId(), namespace, name)
                                .orElseThrow(() -> new RuntimeException("Job not found: " + name));
        }

        public void deleteJob(String clusterUid, String namespace, String name) {
                log.info("Deleting job: {}/{} in cluster UID: {}", namespace, name, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.batch().v1().jobs()
                                .inNamespace(namespace)
                                .withName(name)
                                .delete();

                log.info("Job deleted successfully: {}/{}", namespace, name);
        }

        public List<Job> getJobHistory(String clusterUid, String namespace, String cronJobName) {
                log.info("Getting job history for CronJob: {}/{} in cluster UID: {}",
                                namespace, cronJobName, clusterUid);

                Cluster cluster = clusterRepository.findByUid(clusterUid)
                                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

                // Find jobs owned by this CronJob
                return jobRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
        }

        public void deleteCompletedJobs(String clusterUid, String namespace) {
                log.info("Deleting completed jobs in namespace: {} in cluster UID: {}", namespace, clusterUid);

                KubernetesClient client = clusterContextManager.getClient(clusterUid);

                client.batch().v1().jobs()
                                .inNamespace(namespace)
                                .list()
                                .getItems()
                                .stream()
                                .filter(job -> job.getStatus() != null &&
                                                job.getStatus().getSucceeded() != null &&
                                                job.getStatus().getSucceeded() > 0)
                                .forEach(job -> client.batch().v1().jobs()
                                                .inNamespace(namespace)
                                                .withName(job.getMetadata().getName())
                                                .delete());

                log.info("Completed jobs deleted successfully in namespace: {}", namespace);
        }
}
