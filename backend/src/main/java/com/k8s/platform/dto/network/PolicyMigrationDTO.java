package com.k8s.platform.dto.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyMigrationDTO {

    private Long id;
    private Long policyId;
    private String policyName;
    private String policyNamespace;

    private Integer version;
    private String action; // create, update, delete, rollback

    private String previousYaml;
    private String newYaml;

    private String changeDescription;

    private String appliedBy;
    private LocalDateTime appliedAt;

    private LocalDateTime rollbackAt;
    private String rolledBackBy;

    private LocalDateTime createdAt;

    // Computed fields
    private boolean canRollback;
    private String diffSummary;
}
