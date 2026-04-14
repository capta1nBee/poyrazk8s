package com.k8s.platform.dto.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkFlowStatsDTO {
    private Long totalFlows;
    private Long totalBytes;
    private List<StatItem> byFlowType;
    private List<StatItem> byProtocol;
    private List<StatItem> bySourceNamespace;
    private List<StatItem> byDestinationNamespace;
    private List<TimeSeriesItem> timeSeries;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatItem {
        private String key;
        private Long count;
        private Long bytes;
        private Double percentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesItem {
        private String timestamp;
        private Long count;
        private Long bytes;
        private Map<String, Long> byFlowType;
    }
}
