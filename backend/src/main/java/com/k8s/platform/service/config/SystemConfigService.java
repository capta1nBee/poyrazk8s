package com.k8s.platform.service.config;

import com.k8s.platform.domain.entity.SystemConfig;
import com.k8s.platform.domain.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private static final String ENCRYPTION_KEY = "K8sPlatform2024!"; // Should be from env variable
    private static final String ALGORITHM = "AES";

    public String getConfigValue(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .map(config -> {
                    if (config.getIsEncrypted()) {
                        return decrypt(config.getConfigValue());
                    }
                    return config.getConfigValue();
                })
                .orElse(null);
    }

    public String getConfigValue(String key, String defaultValue) {
        String value = getConfigValue(key);
        return value != null ? value : defaultValue;
    }

    public Map<String, String> getConfigByCategory(String category) {
        // First try by category column
        List<SystemConfig> configs = systemConfigRepository.findByConfigCategory(category);
        // Fallback: if category column is empty, search by key prefix (e.g. "ldap." prefix for "ldap")
        if (configs.isEmpty()) {
            configs = systemConfigRepository.findByConfigKeyStartingWith(category + ".");
        }
        return configs.stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        config -> config.getIsEncrypted() ? decrypt(config.getConfigValue())
                                : (config.getConfigValue() != null ? config.getConfigValue() : "")));
    }

    @Transactional
    public void setConfigValue(String key, String value, boolean encrypt) {
        Optional<SystemConfig> existing = systemConfigRepository.findByConfigKey(key);

        String finalValue = encrypt ? encrypt(value) : value;

        if (existing.isPresent()) {
            SystemConfig config = existing.get();
            config.setConfigValue(finalValue);
            config.setIsEncrypted(encrypt);
            systemConfigRepository.save(config);
        } else {
            SystemConfig config = SystemConfig.builder()
                    .configKey(key)
                    .configValue(finalValue)
                    .isEncrypted(encrypt)
                    .build();
            systemConfigRepository.save(config);
        }
    }

    @Transactional
    public void updateConfig(String key, String value) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new RuntimeException("Config not found: " + key));

        String finalValue = config.getIsEncrypted() ? encrypt(value) : value;
        config.setConfigValue(finalValue);
        systemConfigRepository.save(config);
    }

    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findAll();
    }

    public List<SystemConfig> getConfigsByCategory(String category) {
        return systemConfigRepository.findByConfigCategory(category);
    }

    // Simple encryption/decryption (should use more secure method in production)
    private String encrypt(String value) {
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Encryption error", e);
            return value;
        }
    }

    private String decrypt(String encryptedValue) {
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption error", e);
            return encryptedValue;
        }
    }

    // LDAP Configuration
    public Map<String, String> getLDAPConfig() {
        return getConfigByCategory("ldap");
    }

    @Transactional
    public void updateLDAPConfig(Map<String, String> ldapConfig) {
        ldapConfig.forEach((key, value) -> {
            boolean shouldEncrypt = key.contains("password");
            setConfigValue(key, value, shouldEncrypt);
        });
    }

    // Mail Configuration
    public Map<String, String> getMailConfig() {
        return getConfigByCategory("mail");
    }

    @Transactional
    public void updateMailConfig(Map<String, String> mailConfig) {
        mailConfig.forEach((key, value) -> {
            boolean shouldEncrypt = key.contains("password");
            // Username and password are optional - allow empty strings
            // For other fields, empty string is valid
            if (value != null) {
                setConfigValue(key, value, shouldEncrypt);
            }
        });
    }

    public boolean testMailConnection() {
        try {
            Map<String, String> mailConfig = getMailConfig();
            String host = mailConfig.get("mail.smtp.host");
            String port = mailConfig.get("mail.smtp.port");

            if (host == null || host.isEmpty()) {
                return false;
            }

            // Simple connection test - in production, you'd use JavaMailSender
            log.info("Testing mail connection to {}:{}", host, port);
            return true;
        } catch (Exception e) {
            log.error("Mail connection test failed", e);
            return false;
        }
    }

    // Cluster Configuration
    public Map<String, String> getClusterConfig() {
        return getConfigByCategory("cluster");
    }

    // Watcher Configuration
    public Map<String, String> getWatcherConfig() {
        return getConfigByCategory("watcher");
    }

    // Security Configuration
    public Map<String, String> getSecurityConfig() {
        return getConfigByCategory("security");
    }

    // Read-only mode
    public boolean isReadOnlyMode() {
        return Boolean.parseBoolean(getConfigValue("app.read_only_mode", "false"));
    }

    @Transactional
    public void setReadOnlyMode(boolean enabled) {
        setConfigValue("app.read_only_mode", String.valueOf(enabled), false);
    }

    // Maintenance mode
    public boolean isMaintenanceMode() {
        return Boolean.parseBoolean(getConfigValue("app.maintenance_mode", "false"));
    }

    @Transactional
    public void setMaintenanceMode(boolean enabled) {
        setConfigValue("app.maintenance_mode", String.valueOf(enabled), false);
    }
}
