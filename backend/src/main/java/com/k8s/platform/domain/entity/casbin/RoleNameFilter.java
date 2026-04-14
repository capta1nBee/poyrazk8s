package com.k8s.platform.domain.entity.casbin;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores name-pattern filters for a role/resource combination.
 *
 * When a user accesses a resource list, their effective name patterns are computed
 * from all roles they hold. If ANY role grants wildcard (*), they see everything.
 * Otherwise the union of all patterns from their roles is applied.
 *
 * Example:
 *   role=developer, cluster=prod-uid, nsPattern=test, resourceKind=Pod, namePattern=*kayit*
 *   → user sees only pods whose name contains "kayit" in the test namespace on prod cluster.
 */
@Entity
@Table(name = "role_name_filters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleNameFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The role template name (matches casbin_rule.v0 for ptype='p'). */
    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    /** Cluster UID this filter applies to, '*' = all clusters. */
    @Column(name = "cluster_uid", length = 512)
    @Builder.Default
    private String clusterUid = "*";

    /** Namespace pattern this filter applies to, '*' = all namespaces. */
    @Column(name = "ns_pattern", length = 512)
    @Builder.Default
    private String nsPattern = "*";

    /** The K8s resource kind (e.g. Pod, Deployment, Node). */
    @Column(name = "resource_kind", nullable = false, length = 200)
    private String resourceKind;

    /** Glob-style name pattern (e.g. *kayit*, prod-*, exact-name, *). */
    @Column(name = "name_pattern", nullable = false, length = 512)
    @Builder.Default
    private String namePattern = "*";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
