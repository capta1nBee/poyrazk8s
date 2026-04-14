package com.k8s.platform.dto.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkFlowDTO {
    private Long id;
    private String flowId;
    private String clusterUid;
    private String flowType;
    private LocalDateTime timestamp;

    // Source
    private SourceInfoDTO source;

    // Destination
    private DestinationInfoDTO destination;

    // Network
    private NetworkInfoDTO network;

    // L7
    private L7InfoDTO l7;

    // Metadata
    private String nodeName;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceInfoDTO {
        private String podName;
        private String namespace;
        private String kind;
        private String ip;
        private Integer port;
        private Map<String, String> podLabels;
        private String nodeName;
        private Boolean ingress;
        private Boolean egress;
        private Boolean drop;
        private Boolean pass;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinationInfoDTO {
        private String podName;
        private String namespace;
        private String kind;
        private String ip;
        private Integer port;
        private Map<String, String> podLabels;
        private String nodeName;
        private Boolean ingress;
        private Boolean egress;
        private Boolean drop;
        private Boolean pass;
        private ServiceInfoDTO service;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceInfoDTO {
        private String name;
        private String namespace;
        private Map<String, String> labels;
        private String backendPodName;
        private String backendPodNamespace;
        private Map<String, String> backendPodLabels;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInfoDTO {
        private String protocol;
        private Integer tcpFlags;
        private Long bytes;
        private String interfaceName;
        private String direction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class L7InfoDTO {
        private String protocol;
        private String method;
        private String host;
        private String url;
        private String path;
        private Integer statusCode;
        private String contentType;
    }
}
