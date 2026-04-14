package com.k8s.platform.config.casbin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Configures the Casbin Enforcer with:
 *  - RBAC with domain (cluster) model
 *  - JPA adapter for persistent policy storage
 *  - globMatch built-in function (AntPathMatcher-based)
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CasbinConfig {

    private final JpaAdapter jpaAdapter;

    @Bean
    public Enforcer casbinEnforcer() throws IOException {
        Model model = new Model();
        try (InputStream is = new ClassPathResource("casbin/model.conf").getInputStream();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            model.loadModelFromText(readFully(reader));
        }

        Enforcer enforcer = new Enforcer(model, jpaAdapter);
        enforcer.enableAutoSave(true);

        log.info("Casbin enforcer initialized. Policies loaded: {}",
                enforcer.getPolicy().size());
        return enforcer;
    }

    private String readFully(InputStreamReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
        return sb.toString();
    }
}
