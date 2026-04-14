package com.k8s.platform.domain.dto.appcreator;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GitConnectionDto {
    private UUID id;
    private String provider;
    private String name;
    private String baseUrl;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    // accessToken is intentionally NOT included in the DTO
}

