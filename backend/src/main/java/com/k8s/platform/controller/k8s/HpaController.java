package com.k8s.platform.controller.k8s;

import com.k8s.platform.service.k8s.HpaService;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class HpaController {

    private final HpaService hpaService;
    private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

    // ── List ─────────────────────────────────────────────────────────────────

    @GetMapping("/hpas")
    public ResponseEntity<List<Map<String, Object>>> listAllHpas(
            @PathVariable String clusterUid) {

        authHelper.checkPermissionOrThrow(clusterUid, "*", "HorizontalPodAutoscaler", "*", "view");

        List<HorizontalPodAutoscaler> hpas = hpaService.listHpas(clusterUid);
        List<Map<String, Object>> dtos = hpas.stream().map(this::toMap).toList();

        List<Map<String, Object>> filtered = authHelper.filterAccessibleResources(
                dtos, clusterUid, "HorizontalPodAutoscaler", "view",
                m -> (String) m.get("namespace"), m -> (String) m.get("name"));

        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/namespaces/{namespace}/hpas")
    public ResponseEntity<List<Map<String, Object>>> listHpas(
            @PathVariable String clusterUid,
            @PathVariable String namespace) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "HorizontalPodAutoscaler", "*", "view");

        List<HorizontalPodAutoscaler> hpas = hpaService.listHpas(clusterUid, namespace);
        List<Map<String, Object>> dtos = hpas.stream().map(this::toMap).toList();

        return ResponseEntity.ok(authHelper.filterAccessibleResources(
                dtos, clusterUid, "HorizontalPodAutoscaler", "view",
                m -> (String) m.get("namespace"), m -> (String) m.get("name")));
    }

    // ── Get ──────────────────────────────────────────────────────────────────

    @GetMapping("/namespaces/{namespace}/hpas/{name}")
    public ResponseEntity<HorizontalPodAutoscaler> getHpa(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "HorizontalPodAutoscaler", name, "view");
        return ResponseEntity.ok(hpaService.getHpa(clusterUid, namespace, name));
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @PostMapping("/namespaces/{namespace}/hpas")
    public ResponseEntity<HorizontalPodAutoscaler> createHpa(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @RequestBody HorizontalPodAutoscaler hpa) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "HorizontalPodAutoscaler", "*", "create");
        return ResponseEntity.ok(hpaService.createHpa(clusterUid, namespace, hpa));
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @PutMapping("/namespaces/{namespace}/hpas/{name}")
    public ResponseEntity<HorizontalPodAutoscaler> updateHpa(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody HorizontalPodAutoscaler hpa) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "HorizontalPodAutoscaler", name, "update");
        return ResponseEntity.ok(hpaService.updateHpa(clusterUid, namespace, name, hpa));
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @DeleteMapping("/namespaces/{namespace}/hpas/{name}")
    public ResponseEntity<Map<String, String>> deleteHpa(
            @PathVariable String clusterUid,
            @PathVariable String namespace,
            @PathVariable String name) {

        authHelper.checkPermissionOrThrow(clusterUid, namespace, "HorizontalPodAutoscaler", name, "delete");
        hpaService.deleteHpa(clusterUid, namespace, name);
        return ResponseEntity.ok(Map.of("message", "HPA deleted successfully"));
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(HorizontalPodAutoscaler hpa) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", hpa.getMetadata().getName());
        m.put("namespace", hpa.getMetadata().getNamespace());
        m.put("uid", hpa.getMetadata().getUid());

        // Scale target
        if (hpa.getSpec() != null && hpa.getSpec().getScaleTargetRef() != null) {
            var ref = hpa.getSpec().getScaleTargetRef();
            m.put("targetKind", ref.getKind());
            m.put("targetName", ref.getName());
        }

        m.put("minReplicas", hpa.getSpec() != null ? hpa.getSpec().getMinReplicas() : null);
        m.put("maxReplicas", hpa.getSpec() != null ? hpa.getSpec().getMaxReplicas() : null);
        m.put("currentReplicas", hpa.getStatus() != null ? hpa.getStatus().getCurrentReplicas() : null);
        m.put("desiredReplicas", hpa.getStatus() != null ? hpa.getStatus().getDesiredReplicas() : null);

        // Current metrics summary
        if (hpa.getStatus() != null && hpa.getStatus().getCurrentMetrics() != null) {
            m.put("currentMetrics", hpa.getStatus().getCurrentMetrics());
        }

        // Conditions
        if (hpa.getStatus() != null && hpa.getStatus().getConditions() != null) {
            m.put("conditions", hpa.getStatus().getConditions());
        }

        m.put("labels", hpa.getMetadata().getLabels());
        m.put("annotations", hpa.getMetadata().getAnnotations());
        m.put("k8sCreatedAt", hpa.getMetadata().getCreationTimestamp());

        return m;
    }
}
