package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecSessionResponse {
    private Long id;
    private String sessionId;
    private String clusterId;
    private String namespace;
    private String podName;
    private String status;
    private LocalDateTime createdAt;

    // User info
    private Long userId;
    private String username;
}
