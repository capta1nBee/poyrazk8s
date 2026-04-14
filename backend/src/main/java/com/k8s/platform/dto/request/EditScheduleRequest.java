package com.k8s.platform.dto.request;

import lombok.Data;

@Data
public class EditScheduleRequest {
    private String schedule; // Cron expression
}

