package com.k8s.platform.dto.network;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkFlowFilterDTO {
    private String clusterUid;
    private List<String> flowTypes;
    private List<String> sourceNamespaces;
    private List<String> destinationNamespaces;
    private String sourcePodName;
    private String destinationPodName;
    private List<String> protocols;
    private String sourceIp;
    private String destinationIp;
    private Integer sourcePort;
    private Integer destinationPort;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String l7Method;
    private String l7Path;
    private String serviceName;
    
    // Pagination
    private Integer page;
    private Integer pageSize;
    private String sortBy;
    private Boolean sortDesc;
}
