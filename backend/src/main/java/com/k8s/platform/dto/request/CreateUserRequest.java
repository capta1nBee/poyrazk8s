package com.k8s.platform.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {
    private String username;
    private String email;
    private String password;
    private String authType; // LOCAL | LDAP
    private Boolean isActive = true;
    private Boolean isSuperadmin = false;
    private List<String> roles; // Role names
}

