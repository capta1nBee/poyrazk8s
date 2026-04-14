package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.CronJob;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CronJobResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private String schedule;
    private String concurrencyPolicy; // Allow | Forbid | Replace
    private String lastScheduleTime;
    private Boolean suspend;
    private String k8sCreatedAt;
    private String updatedAt;
    private Boolean isDeleted;
    // Additional UI fields
    private String activeJobs;
    private String labels;
    private String annotations;

    public static CronJobResponseDTO fromEntity(CronJob cronJob) {
        return CronJobResponseDTO.builder()
                .id(cronJob.getId())
                .kind("CronJob")
                .clusterId(cronJob.getClusterId())
                .namespace(cronJob.getNamespace())
                .name(cronJob.getName())
                .schedule(cronJob.getSchedule())
                .concurrencyPolicy(cronJob.getConcurrencyPolicy())
                .lastScheduleTime(cronJob.getLastScheduleTime() != null ? cronJob.getLastScheduleTime().toString() : null)
                .suspend(cronJob.getSuspend())
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .k8sCreatedAt(cronJob.getK8sCreatedAt() != null ? cronJob.getK8sCreatedAt() :
                          (cronJob.getCreatedAt() != null ? cronJob.getCreatedAt().toString() : null))
                .updatedAt(cronJob.getUpdatedAt() != null ? cronJob.getUpdatedAt().toString() : null)
                .isDeleted(cronJob.getIsDeleted() != null ? cronJob.getIsDeleted() : false)
                .activeJobs(cronJob.getActiveJobs())
                .labels(cronJob.getLabels())
                .annotations(cronJob.getAnnotations())
                .build();
    }
}

