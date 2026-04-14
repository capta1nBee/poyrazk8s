package com.k8s.platform.dto.response;

import com.k8s.platform.domain.entity.MonitoringConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class MonitoringConfigResponse {
    private Long id;
    private String clusterUid;
    private Boolean enableExecve;
    private Boolean enableOpen;
    private Boolean enableOpenat;
    private Boolean enableConnect;
    private Boolean enableBind;
    private Boolean enableUnlink;
    private Boolean enableUnlinkat;
    private Boolean enableWrite;
    private Boolean enableLink;
    private Boolean enableRename;
    private Boolean enableMkdir;
    private Boolean enableRmdir;
    private Boolean enableXattr;
    private Boolean enableClone;
    private Boolean enableFork;
    private Boolean enableAccept;
    private Boolean enablePtrace;
    private Boolean enableMount;
    private Map<String, Object> additionalConfig;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public static MonitoringConfigResponse fromEntity(MonitoringConfig config) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> additionalConfig = null;
            if (config.getAdditionalConfigJson() != null) {
                additionalConfig = mapper.readValue(config.getAdditionalConfigJson(), Map.class);
            }

            return MonitoringConfigResponse.builder()
                    .id(config.getId())
                    .clusterUid(config.getClusterUid())
                    .enableExecve(config.getEnableExecve())
                    .enableOpen(config.getEnableOpen())
                    .enableOpenat(config.getEnableOpenat())
                    .enableConnect(config.getEnableConnect())
                    .enableBind(config.getEnableBind())
                    .enableUnlink(config.getEnableUnlink())
                    .enableUnlinkat(config.getEnableUnlinkat())
                    .enableWrite(config.getEnableWrite())
                    .enableLink(config.getEnableLink())
                    .enableRename(config.getEnableRename())
                    .enableMkdir(config.getEnableMkdir())
                    .enableRmdir(config.getEnableRmdir())
                    .enableXattr(config.getEnableXattr())
                    .enableClone(config.getEnableClone())
                    .enableFork(config.getEnableFork())
                    .enableAccept(config.getEnableAccept())
                    .enablePtrace(config.getEnablePtrace())
                    .enableMount(config.getEnableMount())
                    .additionalConfig(additionalConfig)
                    .updatedBy(config.getUpdatedByUser() != null ? config.getUpdatedByUser().getUsername() : null)
                    .updatedAt(config.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            // Log error but return basic response
            return MonitoringConfigResponse.builder()
                    .id(config.getId())
                    .clusterUid(config.getClusterUid())
                    .enableExecve(config.getEnableExecve())
                    .enableOpen(config.getEnableOpen())
                    .enableOpenat(config.getEnableOpenat())
                    .enableConnect(config.getEnableConnect())
                    .enableBind(config.getEnableBind())
                    .updatedAt(config.getUpdatedAt())
                    .build();
        }
    }
}
