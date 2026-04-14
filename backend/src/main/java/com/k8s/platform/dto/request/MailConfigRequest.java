package com.k8s.platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailConfigRequest {
    
    private Boolean enabled;
    
    @NotBlank(message = "SMTP host is required")
    private String host;
    
    private Integer port;
    
    // Username and password are optional
    private String username;
    
    private String password;
    
    private Boolean useTLS;
    
    @NotBlank(message = "From address is required")
    private String fromAddress;
    
    @NotBlank(message = "From name is required")
    private String fromName;
}
