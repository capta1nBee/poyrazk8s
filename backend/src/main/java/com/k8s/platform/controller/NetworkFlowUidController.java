package com.k8s.platform.controller;

import com.k8s.platform.dto.network.*;
import com.k8s.platform.security.ResourceAuthorizationHelper;
import com.k8s.platform.service.network.NetworkFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Network Flow Controller using Cluster UID (same pattern as K8s resources)
 * Uses clusterUid directly - no need to resolve to numeric ID
 */
@RestController
@RequestMapping("/api/network/{clusterUid}")
@RequiredArgsConstructor
@Slf4j
public class NetworkFlowUidController {

    private final NetworkFlowService flowService;
    private final ResourceAuthorizationHelper authHelper;

    /**
     * Query network flows with filters
     */
    @GetMapping("/flows")
    public ResponseEntity<Page<NetworkFlowDTO>> getFlows(
            @PathVariable String clusterUid,
            @RequestParam(required = false) List<String> flowTypes,
            @RequestParam(required = false) List<String> sourceNamespaces,
            @RequestParam(required = false) List<String> destinationNamespaces,
            @RequestParam(required = false) String sourcePodName,
            @RequestParam(required = false) String destinationPodName,
            @RequestParam(required = false) List<String> protocols,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String destinationIp,
            @RequestParam(required = false) Integer sourcePort,
            @RequestParam(required = false) Integer destinationPort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String l7Method,
            @RequestParam(required = false) String l7Path,
            @RequestParam(required = false) String serviceName,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "true") Boolean sortDesc) {

        authHelper.checkPagePermissionOrThrow("network-monitor");

        NetworkFlowFilterDTO filter = NetworkFlowFilterDTO.builder()
                .clusterUid(clusterUid)
                .flowTypes(flowTypes)
                .sourceNamespaces(sourceNamespaces)
                .destinationNamespaces(destinationNamespaces)
                .sourcePodName(sourcePodName)
                .destinationPodName(destinationPodName)
                .protocols(protocols)
                .sourceIp(sourceIp)
                .destinationIp(destinationIp)
                .sourcePort(sourcePort)
                .destinationPort(destinationPort)
                .startTime(startTime)
                .endTime(endTime)
                .l7Method(l7Method)
                .l7Path(l7Path)
                .serviceName(serviceName)
                .page(page)
                .pageSize(pageSize)
                .sortBy(sortBy)
                .sortDesc(sortDesc)
                .build();

        Page<NetworkFlowDTO> flows = flowService.queryFlows(filter);
        return ResponseEntity.ok(flows);
    }

    /**
     * Search flows with POST body filter
     */
    @PostMapping("/flows/search")
    public ResponseEntity<Page<NetworkFlowDTO>> searchFlows(
            @PathVariable String clusterUid,
            @RequestBody NetworkFlowFilterDTO filter) {

        authHelper.checkPagePermissionOrThrow("network-monitor");

        filter.setClusterUid(clusterUid);
        Page<NetworkFlowDTO> flows = flowService.queryFlows(filter);
        return ResponseEntity.ok(flows);
    }

    /**
     * Get flow statistics
     */
    @GetMapping("/flows/stats")
    public ResponseEntity<NetworkFlowStatsDTO> getFlowStats(
            @PathVariable String clusterUid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        authHelper.checkPagePermissionOrThrow("network-monitor");

        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        NetworkFlowStatsDTO stats = flowService.getFlowStats(clusterUid, startTime, endTime);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get filter options (distinct values for dropdowns)
     */
    @GetMapping("/flows/filter-options")
    public ResponseEntity<Map<String, List<String>>> getFilterOptions(
            @PathVariable String clusterUid) {

        authHelper.checkPagePermissionOrThrow("network-monitor");

        Map<String, List<String>> options = flowService.getFilterOptions(clusterUid);
        return ResponseEntity.ok(options);
    }

    /**
     * Get network topology
     */
    @GetMapping("/topology")
    public ResponseEntity<NetworkTopologyDTO> getTopology(
            @PathVariable String clusterUid,
            @RequestParam(required = false) List<String> namespaces) {

        authHelper.checkPagePermissionOrThrow("network-topology");

        NetworkTopologyDTO topology = flowService.getTopology(clusterUid, namespaces);
        return ResponseEntity.ok(topology);
    }

    /**
     * Export flows as JSON (for download)
     */
    @GetMapping("/flows/export")
    public ResponseEntity<List<NetworkFlowDTO>> exportFlows(
            @PathVariable String clusterUid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1000") Integer limit) {

        authHelper.checkPagePermissionOrThrow("network-monitor");

        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        NetworkFlowFilterDTO filter = NetworkFlowFilterDTO.builder()
                .clusterUid(clusterUid)
                .startTime(startTime)
                .endTime(endTime)
                .page(0)
                .pageSize(limit)
                .sortBy("timestamp")
                .sortDesc(true)
                .build();

        Page<NetworkFlowDTO> flows = flowService.queryFlows(filter);
        return ResponseEntity.ok(flows.getContent());
    }
}
