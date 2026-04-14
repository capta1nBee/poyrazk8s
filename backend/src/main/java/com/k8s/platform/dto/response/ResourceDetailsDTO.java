package com.k8s.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDetailsDTO {
    // Metadata
    private String kind;
    private String apiVersion;
    private String name;
    private String namespace;
    private String uid;
    private String resourceVersion;
    private Long generation;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;
    
    // Labels & Annotations
    private Map<String, String> labels;
    private Map<String, String> annotations;
    
    // Owner References
    private Object ownerReferences; // List of owner refs
    
    // Finalizers
    private Object finalizers; // List of finalizers
    
    // Managed Fields
    private Object managedFields; // List of managed fields
    
    // Status (resource-specific)
    private Map<String, Object> status;
    
    // Spec (resource-specific)
    private Map<String, Object> spec;
    
    // YAML
    private String yaml;
    
    // Cluster info
    private Long clusterId;
    private String clusterUid;
    private String clusterName;
}

