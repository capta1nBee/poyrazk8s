package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowedCommandResponse {
    private Long id;
    private String commandPattern;
    private String description;
    private RoleResponse role;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleResponse {
        /** RoleTemplate name used in Casbin (replaces legacy numeric id). */
        private String name;
    }
}
