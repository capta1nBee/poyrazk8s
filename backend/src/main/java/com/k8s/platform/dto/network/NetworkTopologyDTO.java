package com.k8s.platform.dto.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkTopologyDTO {
    private List<TopologyNode> nodes;
    private List<TopologyEdge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopologyNode {
        private String id; // Unique identifier: type:namespace:name
        private String name;
        private String namespace;
        private String type; // pod, service, external, node
        private String label; // Display label
        private Integer connectionCount;
        private Long totalBytes;
        private String group; // For grouping/coloring by namespace
        private java.util.Map<String, String> podLabels; // Exact kubernetes labels
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopologyEdge {
        private Long id;
        private String source; // Source node id
        private String target; // Target node id
        private String protocol;
        private Integer port;
        private Long flowCount;
        private Long totalBytes;
        private LocalDateTime lastSeen;
        private String label; // Display label (e.g., "TCP:80")
        private Double weight; // For edge thickness based on traffic
        private Boolean isLogical; // To indicate Service->Pod implicit routes
    }
}
