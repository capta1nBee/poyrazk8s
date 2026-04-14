package com.k8s.platform.config;

import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.service.cluster.ClusterContextManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupInitializer {

    private final ClusterContextManager clusterContextManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.superadmin.default-password}")
    private String defaultPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application started, initializing clusters...");

        // Initialize Admin Password if missing
        userRepository.findByUsername("admin").ifPresent(user -> {
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                log.info("Initializing admin password...");
                user.setPassword(passwordEncoder.encode(defaultPassword));
                userRepository.save(user);
                log.info("Admin password initialized with MD5.");
            }
        });

        try {
            clusterContextManager.initializeAllClusters();
            log.info("All clusters initialized successfully");
        } catch (Exception e) {
            log.error("Error initializing clusters", e);
        }
    }
}
