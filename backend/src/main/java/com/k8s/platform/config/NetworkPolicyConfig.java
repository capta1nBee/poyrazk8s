package com.k8s.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "network.policy")
public class NetworkPolicyConfig {

    /**
     * Comma-separated list of label keys to use for policy targeting.
     * These labels identify workloads for network policy generation.
     * Default: app,uygulama,deploy,project
     */
    private String policyLabels = "app,uygulama,deploy,project";

    /**
     * Get policy labels as a list
     */
    public List<String> getPolicyLabelsList() {
        return Arrays.stream(policyLabels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
