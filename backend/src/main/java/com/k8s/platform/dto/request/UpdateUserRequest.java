package com.k8s.platform.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UpdateUserRequest {
    private String username;
    private String email;
    private String password; // Optional - only if changing password
    private Boolean isActive;
    private Boolean isSuperadmin;
    private List<String> roles; // Role names
}

