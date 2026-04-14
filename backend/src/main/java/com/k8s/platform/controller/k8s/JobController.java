package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.Job;
import com.k8s.platform.service.k8s.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    @GetMapping("/jobs")
    public ResponseEntity<List<Job>> listAllJobs(
            @PathVariable String clusterUid,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "Job", "*", "view");
        List<Job> allJobs = jobService.listJobs(clusterUid, includeDeleted);

        List<Job> filteredJobs = authHelper.filterAccessibleResources(allJobs, clusterUid, "Job", "view",
                item -> item.getNamespace(), item -> item.getName());

        return ResponseEntity.ok(filteredJobs);
    }

    @GetMapping("/namespaces/{namespace}/jobs")
    public ResponseEntity<List<Job>> listJobs(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Job", "*", "view");
        List<Job> all = jobService.listJobs(clusterUid, namespace, includeDeleted);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Job", "view",
                item -> item.getNamespace(), item -> item.getName()));
    }

    @GetMapping("/namespaces/{namespace}/jobs/{name}")
    public ResponseEntity<Job> getJob(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Job", name, "view");
        return ResponseEntity.ok(jobService.getJob(clusterUid, namespace, name));
    }

    @DeleteMapping("/namespaces/{namespace}/jobs/{name}")
    public ResponseEntity<Map<String, String>> deleteJob(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Job", name, "delete");
        jobService.deleteJob(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "Job deleted successfully"));
    }

    @GetMapping("/namespaces/{namespace}/cronjobs/{cronJobName}/jobs")
    public ResponseEntity<List<Job>> getJobHistory(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String cronJobName) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Job", "*", "view");
        List<Job> allResources = jobService.getJobHistory(clusterUid, namespace, cronJobName);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(allResources, clusterUid, "Job", "view",
                item -> item.getNamespace(), item -> item.getName()));
    }

    @DeleteMapping("/namespaces/{namespace}/jobs/completed")
    public ResponseEntity<Map<String, String>> deleteCompletedJobs(
            @PathVariable String clusterUid,
            @PathVariable String namespace) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "Job", "*", "delete");
        jobService.deleteCompletedJobs(clusterUid, namespace);
        return ResponseEntity.ok(Map.of("message", "Completed jobs deleted successfully"));
    }
}
