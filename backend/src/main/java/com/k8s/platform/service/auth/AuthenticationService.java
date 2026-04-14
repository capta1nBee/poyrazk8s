package com.k8s.platform.service.auth;

import com.k8s.platform.config.JwtTokenProvider;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.dto.request.LoginRequest;
import com.k8s.platform.dto.response.AuthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final LDAPAuthenticationService ldapAuthenticationService;

    public AuthenticationService(
            UserRepository userRepository,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            @Lazy LDAPAuthenticationService ldapAuthenticationService) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.ldapAuthenticationService = ldapAuthenticationService;
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        // First check if user exists and get their auth type
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        
        // If user exists and is LDAP type, authenticate via LDAP
        if (user != null && "LDAP".equalsIgnoreCase(user.getAuthType())) {
            log.info("User {} is LDAP type, authenticating via LDAP", request.getUsername());
            return authenticateLDAPUser(user, request.getPassword());
        }

        // For LOCAL users or new users, use standard authentication
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));
        } catch (Exception e) {
            log.error("Authentication failed for user: {}", request.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }

        // Get user details (re-fetch in case it was null before)
        if (user == null) {
            user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
        }

        return generateAuthResponse(user);
    }
    
    /**
     * Authenticate LDAP user by verifying credentials against LDAP server
     */
    private AuthResponse authenticateLDAPUser(User user, String password) {
        try {
            // Verify password against LDAP
            boolean authenticated = ldapAuthenticationService.verifyLDAPCredentials(user.getUsername(), password);
            
            if (!authenticated) {
                throw new BadCredentialsException("Invalid LDAP credentials");
            }
            
            log.info("LDAP authentication successful for user: {}", user.getUsername());
            return generateAuthResponse(user);
            
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.error("LDAP authentication failed for user: {}", user.getUsername(), e);
            throw new BadCredentialsException("LDAP authentication failed: " + e.getMessage());
        }
    }
    
    /**
     * Generate auth response with JWT token for a user (no password check)
     * Used after LDAP authentication succeeds
     */
    public AuthResponse generateAuthResponse(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("isSuperadmin", user.getIsSuperadmin());
        claims.put("roles", user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toList()));

        String token = jwtTokenProvider.generateToken(user.getUsername(), claims);

        log.info("Token generated for user: {}", user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .isSuperadmin(user.getIsSuperadmin())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName())
                        .collect(Collectors.toList()))
                .build();
    }

    public AuthResponse refreshToken(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        Map<String, Object> claims = new HashMap<>(jwtTokenProvider.getClaimsFromToken(token));
        
        // Remove standard claims to avoid duplication when generating new token
        claims.remove("sub");
        claims.remove("iat");
        claims.remove("exp");

        String newToken = jwtTokenProvider.generateToken(username, claims);
        User user = getCurrentUser(username);

        return AuthResponse.builder()
                .token(newToken)
                .username(user.getUsername())
                .email(user.getEmail())
                .isSuperadmin(user.getIsSuperadmin())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName())
                        .collect(Collectors.toList()))
                .build();
    }

    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
