package com.k8s.platform.controller.k8s;

import com.k8s.platform.domain.entity.k8s.CronJob;
import com.k8s.platform.service.k8s.CronJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class CronJobController {

    private final CronJobService cronJobService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    /**
     * List all cronjobs - filtered by authorized namespaces
     */
    @GetMapping("/cronjobs")
    public ResponseEntity<List<CronJob>> listAllCronJobs(
            @PathVariable String clusterUid,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, "*", "CronJob", "*", "view");
        List<CronJob> allCronJobs = cronJobService.listCronJobs(clusterUid, includeDeleted);

        // Filter by authorized namespaces
        List<CronJob> filteredCronJobs = authHelper.filterAccessibleResources(allCronJobs, clusterUid, "CronJob", "view", item -> item.getNamespace(), item -> item.getName());

        return ResponseEntity.ok(filteredCronJobs);
    }

    @GetMapping("/namespaces/{namespace}/cronjobs")
    public ResponseEntity<List<CronJob>> listCronJobs(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "CronJob", "*", "view");
        List<CronJob> all = cronJobService.listCronJobs(clusterUid, namespace, includeDeleted);
        return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "CronJob", "view", item -> item.getNamespace(), item -> item.getName()));
    }

    @GetMapping("/namespaces/{namespace}/cronjobs/{name}")
    public ResponseEntity<CronJob> getCronJob(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "CronJob", name, "view");
        return ResponseEntity.ok(cronJobService.getCronJob(clusterUid, namespace, name));
    }

    @PostMapping("/namespaces/{namespace}/cronjobs/{name}/run")
    public ResponseEntity<Map<String, String>> runNow(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "CronJob", name, "run-now");
        cronJobService.runNow(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "CronJob triggered successfully"));
    }

    @PostMapping("/namespaces/{namespace}/cronjobs/{name}/suspend")
    public ResponseEntity<Map<String, String>> suspend(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "CronJob", name, "suspend");
        cronJobService.suspend(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "CronJob suspended successfully"));
    }

    @PostMapping("/namespaces/{namespace}/cronjobs/{name}/resume")
    public ResponseEntity<Map<String, String>> resume(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "CronJob", name, "resume");
        cronJobService.resume(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "CronJob resumed successfully"));
    }

    @DeleteMapping("/namespaces/{namespace}/cronjobs/{name}")
    public ResponseEntity<Map<String, String>> deleteCronJob(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {
        authHelper.checkPermissionOrThrow(clusterUid, namespace, "CronJob", name, "delete");
        cronJobService.deleteCronJob(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "CronJob deleted successfully"));
    }
}
