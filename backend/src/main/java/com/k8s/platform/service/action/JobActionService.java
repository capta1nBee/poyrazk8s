package com.k8s.platform.service.action;

import com.k8s.platform.service.cluster.ClusterContextManager;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobActionService {
    private final ClusterContextManager clusterContextManager;

    public void rerun(Long clusterId, String namespace, String jobName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            Job job = client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
            if (job == null) throw new RuntimeException("Job not found: " + jobName);

            job.getMetadata().setResourceVersion(null);
            job.getMetadata().setUid(null);
            job.getMetadata().setName(jobName + "-rerun-" + System.currentTimeMillis());
            job.getStatus().setActive(null);
            job.getStatus().setSucceeded(null);
            job.getStatus().setFailed(null);

            client.batch().v1().jobs().inNamespace(namespace).create(job);
            log.info("Rerun job: {}/{}", namespace, jobName);
        } catch (Exception e) {
            log.error("Failed to rerun job: {}/{}", namespace, jobName, e);
            throw new RuntimeException("Failed to rerun job: " + e.getMessage(), e);
        }
    }

    public void terminate(Long clusterId, String namespace, String jobName) {
        try {
            KubernetesClient client = clusterContextManager.getClient(clusterId);
            client.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
            log.info("Terminated job: {}/{}", namespace, jobName);
        } catch (Exception e) {
            log.error("Failed to terminate job: {}/{}", namespace, jobName, e);
            throw new RuntimeException("Failed to terminate job: " + e.getMessage(), e);
        }
    }
}
