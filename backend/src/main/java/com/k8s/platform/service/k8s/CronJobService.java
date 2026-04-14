package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.k8s.CronJob;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.domain.repository.k8s.CronJobRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronJobService {

    private final CronJobRepository cronJobRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterContextManager clusterContextManager;

    public List<CronJob> listCronJobs(String clusterUid) {
        return listCronJobs(clusterUid, false);
    }

    public List<CronJob> listCronJobs(String clusterUid, boolean includeDeleted) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        if (includeDeleted) {
            return cronJobRepository.findByClusterId(cluster.getId());
        }
        return cronJobRepository.findByClusterIdAndIsDeletedFalse(cluster.getId());
    }

    public List<CronJob> listCronJobs(String clusterUid, String namespace) {
        return listCronJobs(clusterUid, namespace, false);
    }

    public List<CronJob> listCronJobs(String clusterUid, String namespace, boolean includeDeleted) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        if (includeDeleted) {
            return cronJobRepository.findByClusterIdAndNamespace(cluster.getId(), namespace);
        }
        return cronJobRepository.findByClusterIdAndNamespaceAndIsDeletedFalse(cluster.getId(), namespace);
    }

    public CronJob getCronJob(String clusterUid, String namespace, String name) {
        Cluster cluster = clusterRepository.findByUid(clusterUid)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterUid));

        return cronJobRepository.findByClusterIdAndNamespaceAndNameAndIsDeletedFalse(
                cluster.getId(), namespace, name)
                .orElseThrow(() -> new RuntimeException("CronJob not found: " + name));
    }

    public void runNow(String clusterUid, String namespace, String name) {
        log.info("Triggering CronJob now: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        // CronJobs are in batch/v1 API
        var cronJobList = client.batch().v1().cronjobs()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (cronJobList != null && cronJobList.getSpec() != null && cronJobList.getSpec().getJobTemplate() != null) {
            // Create a job from the CronJob template
            var job = new JobBuilder()
                    .withNewMetadata()
                    .withName(name + "-manual-" + System.currentTimeMillis())
                    .withNamespace(namespace)
                    .endMetadata()
                    .withSpec(cronJobList.getSpec().getJobTemplate().getSpec())
                    .build();

            client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
            log.info("CronJob triggered successfully: {}/{}", namespace, name);
        }
    }

    public void suspend(String clusterUid, String namespace, String name) {
        log.info("Suspending CronJob: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        var cronJob = client.batch().v1().cronjobs()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (cronJob != null && cronJob.getSpec() != null) {
            cronJob.getSpec().setSuspend(true);
            client.batch().v1().cronjobs()
                    .inNamespace(namespace)
                    .resource(cronJob)
                    .update();
        }

        log.info("CronJob suspended successfully: {}/{}", namespace, name);
    }

    public void resume(String clusterUid, String namespace, String name) {
        log.info("Resuming CronJob: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        var cronJob = client.batch().v1().cronjobs()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (cronJob != null && cronJob.getSpec() != null) {
            cronJob.getSpec().setSuspend(false);
            client.batch().v1().cronjobs()
                    .inNamespace(namespace)
                    .resource(cronJob)
                    .update();
        }

        log.info("CronJob resumed successfully: {}/{}", namespace, name);
    }

    public void deleteCronJob(String clusterUid, String namespace, String name) {
        log.info("Deleting CronJob: {}/{} in cluster UID: {}", namespace, name, clusterUid);

        KubernetesClient client = clusterContextManager.getClient(clusterUid);

        client.batch().v1().cronjobs()
                .inNamespace(namespace)
                .withName(name)
                .delete();

        log.info("CronJob deleted successfully: {}/{}", namespace, name);
    }
}
