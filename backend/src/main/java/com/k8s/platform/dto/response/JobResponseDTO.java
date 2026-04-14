package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.k8s.platform.domain.entity.k8s.Job;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponseDTO {
    private Long id;
    private String kind;
    private Long clusterId;
    private String namespace;
    private String name;
    private String status; // Running | Succeeded | Failed
    private Integer completions;
    private Integer parallelism;
    private String startTime;
    private String completionTime;
    private String createdAt;
    private String updatedAt;
    // Additional UI fields
    private Integer active;
    private Integer succeeded;
    private Integer failed;
    private String labels;
    private String annotations;

    public static JobResponseDTO fromEntity(Job job) {
        return JobResponseDTO.builder()
                .id(job.getId())
                .kind("Job")
                .clusterId(job.getClusterId())
                .namespace(job.getNamespace())
                .name(job.getName())
                .status(job.getStatus())
                .completions(job.getCompletions())
                .parallelism(job.getParallelism())
                .startTime(job.getStartTime() != null ? job.getStartTime().toString() : null)
                .completionTime(job.getCompletionTime() != null ? job.getCompletionTime().toString() : null)
                // Use K8s creationTimestamp if available, fallback to DB createdAt
                .createdAt(job.getK8sCreatedAt() != null ? job.getK8sCreatedAt() :
                          (job.getCreatedAt() != null ? job.getCreatedAt().toString() : null))
                .updatedAt(job.getUpdatedAt() != null ? job.getUpdatedAt().toString() : null)
                .active(job.getActive())
                .succeeded(job.getSucceeded())
                .failed(job.getFailed())
                .labels(job.getLabels())
                .annotations(job.getAnnotations())
                .build();
    }
}

