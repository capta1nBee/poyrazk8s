package com.k8s.platform.dto.request;

import lombok.Data;

@Data
public class RecordingRequest {
    private String eventData; // JSON from rrweb
}
