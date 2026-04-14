package com.k8s.platform.controller;

import com.k8s.platform.dto.request.LoginRequest;
import com.k8s.platform.dto.response.AuthResponse;
import com.k8s.platform.service.auth.AuthenticationService;
import com.k8s.platform.service.policy.UIPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UIPermissionService uiPermissionService;
    private final com.k8s.platform.service.audit.AuditLogService auditLogService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authenticationService.login(request);
        auditLogService.log(request.getUsername(), "login", "User logged in successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        if (authentication != null) {
            auditLogService.log(authentication.getName(), "logout", "User logged out");
        }
        // JWT is stateless, logout is handled on client side
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(Authentication authentication) {
        var user = authenticationService.getCurrentUser(authentication.getName());

        return ResponseEntity.ok(AuthResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .isSuperadmin(user.getIsSuperadmin())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName())
                        .toList())
                .build());
    }

    @GetMapping("/me/pages")
    public ResponseEntity<Map<String, Object>> getCurrentUserPages(Authentication authentication) {
        String username = authentication.getName();
        Set<String> pages = uiPermissionService.getUserPages(username);
        List<String> features = uiPermissionService.getUserFeatures(username);

        return ResponseEntity.ok(Map.of(
                "pages", pages,
                "features", features));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return ResponseEntity.ok(authenticationService.refreshToken(token));
    }
}
