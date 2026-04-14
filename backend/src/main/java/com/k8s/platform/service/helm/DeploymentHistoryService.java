package com.k8s.platform.service.helm;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeploymentHistoryService {

    private final Map<String, List<DeploymentRecord>> historyStore = new ConcurrentHashMap<>();

    public void record(String clusterUid, DeploymentRecord record) {
        historyStore.computeIfAbsent(clusterUid, k -> new ArrayList<>()).add(record);
    }

    public List<DeploymentRecord> getHistory(String clusterUid) {
        return historyStore.getOrDefault(clusterUid, Collections.emptyList());
    }

    @Data
    @Builder
    public static class DeploymentRecord {
        private String releaseName;
        private String namespace;
        private String chartName;
        private String chartVersion;
        private String deployName;
        private String status; // DEPLOYED, UPGRADED, FAILED, UNINSTALLED
        private LocalDateTime timestamp;
        private String logs;
    }
}
