package com.k8s.platform.dto.response.federation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FederationResponse {
    private Long id;
    private String name;
    private Long masterClusterId;
    private String masterClusterName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<MemberClusterDto> members;
    private List<ResourceDto> resources;

    @Data
    @Builder
    public static class MemberClusterDto {
        private Long id;
        private Long clusterId;
        private String clusterName;
    }

    @Data
    @Builder
    public static class ResourceDto {
        private Long id;
        private String kind;
        private String namespace;
        private String name;
        private String syncStatus;
        private String errorMessage;
        private LocalDateTime lastErrorTime;
        private LocalDateTime lastSyncTime;
        private String previousStateYaml;
        private String dependencyStatus;
    }
}
