package com.k8s.platform.domain.dto.helm;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class HelmRepositoryDto {
    private UUID id;
    private String clusterUid;
    private String name;
    private String url;
    private boolean isPrivate;
    // We explicitly omit the password for security
    private String username;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
