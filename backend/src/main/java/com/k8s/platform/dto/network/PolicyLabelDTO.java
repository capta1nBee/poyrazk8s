package com.k8s.platform.dto.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyLabelDTO {
    
    /**
     * The label key (e.g., "app", "uygulama")
     */
    private String labelKey;
    
    /**
     * The label value (e.g., "frontend", "backend")
     */
    private String labelValue;
    
    /**
     * Display name for UI (key=value format)
     */
    private String displayName;
    
    /**
     * Number of flows associated with this label
     */
    private Long flowCount;
    
    /**
     * Pod names that have this label (for reference)
     */
    private List<String> podNames;
    
    /**
     * Whether this is from a service backend
     */
    private boolean fromService;
    
    /**
     * Service name if this label is from a service's selector
     */
    private String serviceName;
}
