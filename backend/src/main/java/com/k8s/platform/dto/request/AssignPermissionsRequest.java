package com.k8s.platform.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignPermissionsRequest {
    private Long userId;

    @Builder.Default
    private List<Assignment> assignments = new ArrayList<>();

    @Builder.Default
    private UIPermissions uiPermissions = new UIPermissions();

    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assignment {
        private String cluster; // cluster name or UID
        private String namespace; // namespace name or * for all

        @Builder.Default
        private List<Resource> resources = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Resource {
        private String kind; // Pod, Deployment, Service, etc.

        @Builder.Default
        private String namePattern = "*"; // Wildcard pattern: *, a*, *test*, etc.

        @Builder.Default
        private List<String> actions = new ArrayList<>(); // get, list, create, update, delete, patch, exec, logs, portforward
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UIPermissions {
        @Builder.Default
        private List<String> pages = new ArrayList<>(); // dashboard, clusters, namespaces, pods, deployments, etc.

        @Builder.Default
        private List<String> features = new ArrayList<>(); // create, edit, delete, exec, logs, portforward, admin
    }
}

