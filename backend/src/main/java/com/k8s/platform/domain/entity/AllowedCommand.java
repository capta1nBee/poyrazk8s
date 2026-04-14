package com.k8s.platform.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "allowed_commands")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowedCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Legacy FK — nullable after V96 migration. Kept for audit history. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = true)
    private Role role;

    /** New: Casbin RoleTemplate name used for permission checks (replaces role FK). */
    @Column(name = "role_template_name", length = 100)
    private String roleTemplateName;

    @Column(name = "command_pattern", nullable = false)
    private String commandPattern;

    @Column
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
