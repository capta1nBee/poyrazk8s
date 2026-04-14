package com.k8s.platform.service.action;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronJobActionService {
    private final ClusterContextManager clusterContextManager;

    public void runNow(Long clusterId, String namespace, String cronJobName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            CronJob cronJob = client.resources(CronJob.class).inNamespace(namespace).withName(cronJobName).get();
            if (cronJob == null) throw new RuntimeException("CronJob not found: " + cronJobName);

            io.fabric8.kubernetes.api.model.batch.v1.Job job = new io.fabric8.kubernetes.api.model.batch.v1.JobBuilder()
                .withNewMetadata()
                    .withName(cronJobName + "-manual-" + System.currentTimeMillis())
                    .withNamespace(namespace)
                .endMetadata()
                .withSpec(cronJob.getSpec().getJobTemplate().getSpec())
                .build();

            client.batch().v1().jobs().inNamespace(namespace).create(job);
            log.info("Manually triggered cronjob: {}/{}", namespace, cronJobName);
        } catch (Exception e) {
            log.error("Failed to run cronjob: {}/{}", namespace, cronJobName, e);
            throw new RuntimeException("Failed to run cronjob: " + e.getMessage(), e);
        }
    }

    public void suspend(Long clusterId, String namespace, String cronJobName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            CronJob cronJob = client.resources(CronJob.class).inNamespace(namespace).withName(cronJobName).get();
            if (cronJob == null) throw new RuntimeException("CronJob not found: " + cronJobName);
            cronJob.getSpec().setSuspend(true);
            client.resources(CronJob.class).inNamespace(namespace).withName(cronJobName).replace(cronJob);
            log.info("Suspended cronjob: {}/{}", namespace, cronJobName);
        } catch (Exception e) {
            log.error("Failed to suspend cronjob: {}/{}", namespace, cronJobName, e);
            throw new RuntimeException("Failed to suspend cronjob: " + e.getMessage(), e);
        }
    }

    public void resume(Long clusterId, String namespace, String cronJobName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            CronJob cronJob = client.resources(CronJob.class).inNamespace(namespace).withName(cronJobName).get();
            if (cronJob == null) throw new RuntimeException("CronJob not found: " + cronJobName);
            cronJob.getSpec().setSuspend(false);
            client.resources(CronJob.class).inNamespace(namespace).withName(cronJobName).replace(cronJob);
            log.info("Resumed cronjob: {}/{}", namespace, cronJobName);
        } catch (Exception e) {
            log.error("Failed to resume cronjob: {}/{}", namespace, cronJobName, e);
            throw new RuntimeException("Failed to resume cronjob: " + e.getMessage(), e);
        }
    }
}
