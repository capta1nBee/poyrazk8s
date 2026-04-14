package com.k8s.platform.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PolicyRequest {

    private Long userId;
    private String subjectType;
    private String subjectName;
    private List<AssignmentRequest> assignments;
    private UIPermissionsRequest uiPermissions;
    private List<String> roles;

    @Data
    public static class AssignmentRequest {
        private String cluster;
        private String namespace;
        private List<ResourceRequest> resources;
    }

    @Data
    public static class ResourceRequest {
        private String kind;
        private String namePattern;
        private List<String> actions;
    }

    @Data
    public static class UIPermissionsRequest {
        private List<String> pages;
    }
}
