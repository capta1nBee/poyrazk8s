package com.k8s.platform.domain.entity.appcreator;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appcreator_drafts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCreatorDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cluster_uid", nullable = false)
    private String clusterUid;

    @Column(name = "app_id")
    private UUID appId;

    // @Lob
    @Column(name = "wizard_state", nullable = false)
    private String wizardState;

    @Column(name = "current_step")
    @Builder.Default
    private Integer currentStep = 1;

    @Column(name = "created_by")
    private Long createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
