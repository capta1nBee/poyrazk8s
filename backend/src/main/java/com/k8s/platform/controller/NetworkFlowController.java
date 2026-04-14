package com.k8s.platform.controller;

import com.k8s.platform.annotation.RequirePermission;
import com.k8s.platform.dto.network.*;
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
 * Network Flow Controller - Uses clusterUid for all operations
 * The {clusterId} path variable is kept for backward compatibility but we use
 * clusterUid from payload
 */
@RestController
@RequestMapping("/api/v1/clusters/{clusterId}/network")
@RequiredArgsConstructor
@Slf4j
public class NetworkFlowController {

    private final NetworkFlowService flowService;

    /**
     * Receive flows batch from network-sniffer agent
     * Agent sends clusterUid in the payload - we use that directly
     */
    @PostMapping("/flows/batch")
    public ResponseEntity<Map<String, Object>> receiveFlowsBatch(
            @PathVariable String clusterId,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Cluster-UID", required = false) String clusterUidHeader,
            @RequestHeader(value = "X-Node-Name", required = false) String nodeNameHeader) {

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = (List<Map<String, Object>>) payload.get("events");
            String nodeName = nodeNameHeader != null ? nodeNameHeader
                    : (String) payload.getOrDefault("nodeName", "unknown");

            // Get clusterUid - prefer header, then payload
            String clusterUid = clusterUidHeader != null ? clusterUidHeader : (String) payload.get("clusterUid");

            // If clusterUid is still null, use clusterId from path (backward compatibility)
            if (clusterUid == null || clusterUid.isEmpty()) {
                clusterUid = clusterId;
            }

            if (events == null || events.isEmpty()) {
                log.debug("Received empty batch from node {} (cluster UID: {})", nodeName, clusterUid);
                return ResponseEntity.ok(Map.of("success", true, "message", "No events", "count", 0));
            }

            log.info("========================================");
            log.info("FLOW BATCH RECEIVED");
            log.info("  Cluster UID: {}", clusterUid);
            log.info("  Node Name: {}", nodeName);
            log.info("  Event Count: {}", events.size());
            log.info("========================================");

            // Log sample of flow types
            Map<String, Long> flowTypeCounts = events.stream()
                    .map(e -> (String) e.getOrDefault("flowType", "unknown"))
                    .collect(java.util.stream.Collectors.groupingBy(t -> t, java.util.stream.Collectors.counting()));
            log.info("Flow types in batch: {}", flowTypeCounts);

            // Convert and save
            List<NetworkFlowDTO> dtos = events.stream()
                    .map(this::mapToDTO)
                    .collect(java.util.stream.Collectors.toList());

            int saved = flowService.saveFlowBatch(dtos, clusterUid, nodeName);

            log.info("Successfully saved {} flows to database", saved);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Batch saved successfully",
                    "count", saved,
                    "clusterUid", clusterUid,
                    "nodeName", nodeName));
        } catch (Exception e) {
            log.error("Error processing flow batch: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private NetworkFlowDTO mapToDTO(Map<String, Object> map) {
        // Parse timestamp with multiple format support
        java.time.LocalDateTime timestamp = parseTimestamp((String) map.get("timestamp"));

        NetworkFlowDTO.NetworkFlowDTOBuilder builder = NetworkFlowDTO.builder()
                .flowId((String) map.get("flowId"))
                .flowType((String) map.get("flowType"))
                .timestamp(timestamp)
                .nodeName((String) map.get("nodeName"));

        // Source
        Map<String, Object> source = (Map<String, Object>) map.get("source");
        if (source != null) {
            builder.source(NetworkFlowDTO.SourceInfoDTO.builder()
                    .podName((String) source.get("podName"))
                    .namespace((String) source.get("namespace"))
                    .kind((String) source.get("kind"))
                    .ip((String) source.get("ip"))
                    .port(source.get("port") != null ? ((Number) source.get("port")).intValue() : 0)
                    .nodeName((String) source.get("nodeName"))

                    .ingress((Boolean) source.getOrDefault("ingress", false))
                    .egress((Boolean) source.getOrDefault("egress", false))
                    .build());
        }

        // Destination
        Map<String, Object> dest = (Map<String, Object>) map.get("destination");
        if (dest != null) {
            NetworkFlowDTO.DestinationInfoDTO.DestinationInfoDTOBuilder destBuilder = NetworkFlowDTO.DestinationInfoDTO
                    .builder()
                    .podName((String) dest.get("podName"))
                    .namespace((String) dest.get("namespace"))
                    .kind((String) dest.get("kind"))
                    .ip((String) dest.get("ip"))
                    .port(dest.get("port") != null ? ((Number) dest.get("port")).intValue() : 0)
                    .nodeName((String) dest.get("nodeName"))

                    .ingress((Boolean) dest.getOrDefault("ingress", false))
                    .egress((Boolean) dest.getOrDefault("egress", false));

            // Service
            Map<String, Object> svc = (Map<String, Object>) dest.get("service");
            if (svc != null) {
                destBuilder.service(NetworkFlowDTO.ServiceInfoDTO.builder()
                        .name((String) svc.get("name"))
                        .namespace((String) svc.get("namespace"))
                        .backendPodName((String) svc.get("backendPodName"))
                        .backendPodNamespace((String) svc.get("backendPodNamespace"))

                        .build());
            }

            builder.destination(destBuilder.build());
        }

        // Network
        Map<String, Object> net = (Map<String, Object>) map.get("network");
        if (net != null) {
            builder.network(NetworkFlowDTO.NetworkInfoDTO.builder()
                    .protocol((String) net.get("protocol"))
                    .tcpFlags(net.get("tcpFlags") != null ? ((Number) net.get("tcpFlags")).intValue() : null)
                    .bytes(net.get("bytes") != null ? ((Number) net.get("bytes")).longValue() : 0L)
                    .interfaceName((String) net.get("interfaceName"))
                    .direction((String) net.get("direction"))
                    .build());
        }

        // L7
        Map<String, Object> l7 = (Map<String, Object>) map.get("l7");
        if (l7 != null && !l7.isEmpty()) {
            builder.l7(NetworkFlowDTO.L7InfoDTO.builder()
                    .protocol((String) l7.get("protocol"))
                    .method((String) l7.get("method"))
                    .host((String) l7.get("host"))
                    .url((String) l7.get("url"))
                    .path((String) l7.get("path"))
                    .statusCode(l7.get("statusCode") != null ? ((Number) l7.get("statusCode")).intValue() : null)
                    .contentType((String) l7.get("contentType"))
                    .build());
        }

        return builder.build();
    }

    /**
     * Parse timestamp from various formats
     */
    private java.time.LocalDateTime parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isEmpty()) {
            return java.time.LocalDateTime.now();
        }

        try {
            // Try ISO_DATE_TIME first (2026-01-26T12:00:00)
            return java.time.LocalDateTime.parse(timestampStr, java.time.format.DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e1) {
            try {
                // Try with nanoseconds (2026-01-26T12:00:00.123456789)
                return java.time.LocalDateTime.parse(timestampStr,
                        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                try {
                    // Try parsing as ZonedDateTime and convert to LocalDateTime
                    java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(timestampStr);
                    return zdt.toLocalDateTime();
                } catch (Exception e3) {
                    try {
                        // Try parsing as OffsetDateTime
                        java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(timestampStr);
                        return odt.toLocalDateTime();
                    } catch (Exception e4) {
                        log.warn("Could not parse timestamp: {}, using current time", timestampStr);
                        return java.time.LocalDateTime.now();
                    }
                }
            }
        }
    }
}
