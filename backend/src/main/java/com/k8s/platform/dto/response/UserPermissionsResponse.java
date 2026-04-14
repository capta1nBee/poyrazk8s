package com.k8s.platform.dto.response;

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
public class UserPermissionsResponse {
    private Long userId;
    private String username;
    private Subject subject;

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
    public static class Subject {
        private String type;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assignment {
        private String cluster;
        private String namespace;

        @Builder.Default
        private List<Resource> resources = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Resource {
        private String kind;

        @Builder.Default
        private String namePattern = "*";

        @Builder.Default
        private List<String> actions = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UIPermissions {
        @Builder.Default
        private List<String> pages = new ArrayList<>();

        @Builder.Default
        private List<String> features = new ArrayList<>();
    }
}

