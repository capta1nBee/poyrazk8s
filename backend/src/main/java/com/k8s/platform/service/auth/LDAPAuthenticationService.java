package com.k8s.platform.service.auth;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.UserRepository;
import com.k8s.platform.service.config.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.OrFilter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LDAPAuthenticationService {

    private final SystemConfigService systemConfigService;
    private final UserRepository userRepository;

    /**
     * Verify user credentials against LDAP server
     * Returns true if authentication succeeds, false otherwise
     */
    public boolean verifyLDAPCredentials(String username, String password) {
        log.info("Verifying LDAP credentials for user: {}", username);

        if (!isLDAPEnabled()) {
            throw new RuntimeException("LDAP authentication is not enabled");
        }

        try {
            Map<String, String> ldapConfig = systemConfigService.getLDAPConfig();
            LdapContextSource contextSource = createContextSource(ldapConfig);
            LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
            ldapTemplate.setIgnorePartialResultException(true);

            // Build search filter for both uid and sAMAccountName (AD support)
            OrFilter orFilter = new OrFilter();
            orFilter.or(new EqualsFilter("uid", username));
            orFilter.or(new EqualsFilter("sAMAccountName", username));
            
            AndFilter filter = new AndFilter();
            filter.and(orFilter);

            // Attempt bind (authentication)
            boolean authenticated = ldapTemplate.authenticate("", filter.encode(), password);

            log.info("LDAP credential verification for user {}: {}", username, authenticated ? "success" : "failed");
            return authenticated;

        } catch (Exception e) {
            log.error("LDAP credential verification failed for user: {}", username, e);
            return false;
        }
    }

    /**
     * Test LDAP connection
     */
    public boolean testLDAPConnection() {
        try {
            Map<String, String> ldapConfig = systemConfigService.getLDAPConfig();
            LdapContextSource contextSource = createContextSource(ldapConfig);
            LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
            ldapTemplate.setIgnorePartialResultException(true);

            // Try to search base DN with a simple filter
            ldapTemplate.search("", "(objectClass=*)", (Object ctx) -> null);

            log.info("LDAP connection test successful");
            return true;
        } catch (Exception e) {
            log.error("LDAP connection test failed", e);
            return false;
        }
    }

    /**
     * Configure LDAP settings (SUPERADMIN only)
     */
    public void configureLDAP(Map<String, String> ldapConfig) {
        log.info("Updating LDAP configuration");
        systemConfigService.updateLDAPConfig(ldapConfig);
    }

    /**
     * Get current LDAP configuration
     */
    public Map<String, String> getLDAPConfig() {
        return systemConfigService.getLDAPConfig();
    }

    /**
     * Check if LDAP is enabled
     */
    public boolean isLDAPEnabled() {
        return Boolean.parseBoolean(systemConfigService.getConfigValue("ldap.enabled", "false"));
    }

    /**
     * Enable/disable LDAP authentication
     */
    public void setLDAPEnabled(boolean enabled) {
        systemConfigService.setConfigValue("ldap.enabled", String.valueOf(enabled), false);
    }

    /**
     * Sync users from LDAP
     */
    public int syncUsersFromLDAP() {
        if (!isLDAPEnabled()) {
            throw new RuntimeException("LDAP is not enabled");
        }

        try {
            Map<String, String> ldapConfig = systemConfigService.getLDAPConfig();
            LdapContextSource contextSource = createContextSource(ldapConfig);
            LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
            ldapTemplate.setIgnorePartialResultException(true);

            String configuredFilter = ldapConfig.getOrDefault("ldap.user.search.filter", "(objectClass=person)");
            
            // If configured filter contains placeholders like {0}, use a generic user filter instead
            String syncFilter;
            if (configuredFilter.contains("{0}") || configuredFilter.contains("{1}")) {
                // This is an authentication filter, not a sync filter
                // Use a generic filter for AD: user objects that are not disabled
                syncFilter = "(&(objectClass=user)(objectCategory=person)(!(userAccountControl:1.2.840.113556.1.4.803:=2)))";
                log.info("Configured filter contains placeholders, using sync filter: {}", syncFilter);
            } else {
                syncFilter = configuredFilter;
            }

            log.info("Syncing users from LDAP with filter: {}", syncFilter);

            // Search for all users in LDAP
            // Use empty string as base since context already has base DN set
            List<Map<String, String>> users = ldapTemplate.search(
                    "",
                    syncFilter,
                    (AttributesMapper<Map<String, String>>) attrs -> {
                        Map<String, String> user = new HashMap<>();
                        try {
                            // Try different username attributes (OpenLDAP vs AD)
                            String username = getAttributeValue(attrs, "sAMAccountName");
                            if (username == null) {
                                username = getAttributeValue(attrs, "uid");
                            }
                            if (username == null) {
                                username = getAttributeValue(attrs, "cn");
                            }
                            
                            String email = getAttributeValue(attrs, "mail");
                            
                            if (username != null) {
                                user.put("username", username);
                                user.put("email", email != null ? email : username + "@ldap.local");
                            }
                        } catch (Exception e) {
                            log.debug("Error reading LDAP attributes: {}", e.getMessage());
                        }
                        return user;
                    }
            );
            
            // Filter out empty results
            List<Map<String, String>> validUsers = users.stream()
                    .filter(u -> u.containsKey("username") && !u.get("username").isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            
            log.info("Found {} users in LDAP", validUsers.size());

            int syncedCount = 0;
            for (Map<String, String> ldapUser : validUsers) {
                String username = ldapUser.get("username");
                String email = ldapUser.getOrDefault("email", username + "@ldap.local");
                
                if (username != null && !username.isEmpty()) {
                    // Check if user already exists
                    if (!userRepository.existsByUsername(username)) {
                        // Create new LDAP user - no password needed, auth is via LDAP
                        User user = User.builder()
                                .username(username)
                                .email(email)
                                .password("") // Empty password - LDAP users authenticate via LDAP server
                                .authType("LDAP")
                                .isActive(true)
                                .isSuperadmin(false)
                                .roles(new HashSet<>())
                                .build();

                        userRepository.save(user);
                        syncedCount++;
                        log.info("Created LDAP user: {} ({})", username, email);
                    }
                }
            }

            log.info("LDAP user sync completed: {} users synced", syncedCount);
            return syncedCount;
        } catch (Exception e) {
            log.error("LDAP user sync failed", e);
            throw new RuntimeException("LDAP sync failed: " + e.getMessage());
        }
    }

    /**
     * Search LDAP users by query string
     */
    public List<Map<String, String>> searchLDAPUsers(String query, int limit) {
        log.info("Searching LDAP users with query: {}, limit: {}", query, limit);
        
        List<Map<String, String>> results = new ArrayList<>();
        
        try {
            Map<String, String> ldapConfig = systemConfigService.getLDAPConfig();
            LdapContextSource contextSource = createContextSource(ldapConfig);
            LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
            // Ignore partial result exceptions for Active Directory
            ldapTemplate.setIgnorePartialResultException(true);
            
            String userSearchBase = ldapConfig.get("ldap.user.search.base");
            String configuredFilter = ldapConfig.getOrDefault("ldap.user.search.filter", "(objectClass=person)");
            
            // If configured filter contains placeholders like {0}, use a generic user filter instead
            String baseFilter;
            if (configuredFilter.contains("{0}") || configuredFilter.contains("{1}")) {
                // This is an authentication filter, not a search filter
                // Use a generic filter for AD or OpenLDAP
                baseFilter = "(objectClass=user)"; // Works for Active Directory
                log.info("Configured filter contains placeholders, using default: {}", baseFilter);
            } else {
                baseFilter = configuredFilter;
            }
            
            // Build search filter - search by cn, uid, or sAMAccountName
            String searchFilter;
            if (query == null || query.trim().isEmpty()) {
                // Return all users matching base filter
                searchFilter = baseFilter;
            } else {
                String queryWildcard = "*" + query.trim() + "*";
                // Support both OpenLDAP (uid, cn) and Active Directory (sAMAccountName)
                searchFilter = String.format("(&%s(|(uid=%s)(cn=%s)(sAMAccountName=%s)(mail=%s)(displayName=%s)))",
                        baseFilter, queryWildcard, queryWildcard, queryWildcard, queryWildcard, queryWildcard);
            }
            
            log.info("LDAP search filter: {}", searchFilter);
            
            // Search for users - search from base DN (empty string since base is set in context)
            List<Map<String, String>> ldapUsers = ldapTemplate.search(
                    "",
                    searchFilter,
                    (AttributesMapper<Map<String, String>>) attrs -> {
                        Map<String, String> user = new HashMap<>();
                        try {
                            // Try different username attributes (OpenLDAP vs AD)
                            String username = getAttributeValue(attrs, "sAMAccountName");
                            if (username == null) {
                                username = getAttributeValue(attrs, "uid");
                            }
                            if (username == null) {
                                username = getAttributeValue(attrs, "cn");
                            }
                            
                            String email = getAttributeValue(attrs, "mail");
                            String displayName = getAttributeValue(attrs, "displayName");
                            if (displayName == null) {
                                displayName = getAttributeValue(attrs, "cn");
                            }
                            
                            if (username != null) {
                                user.put("username", username);
                                user.put("email", email != null ? email : username + "@ldap.local");
                                user.put("displayName", displayName != null ? displayName : username);
                            }
                        } catch (Exception e) {
                            log.debug("Error reading LDAP attributes: {}", e.getMessage());
                        }
                        return user;
                    }
            );
            
            // Filter out empty results and limit
            results = ldapUsers.stream()
                    .filter(u -> u.containsKey("username") && !u.get("username").isEmpty())
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toList());
            
            log.info("LDAP search found {} users", results.size());
            
        } catch (Exception e) {
            log.error("LDAP user search failed: {}", e.getMessage());
            throw new RuntimeException("LDAP search failed: " + e.getMessage());
        }
        
        return results;
    }
    
    private String getAttributeValue(javax.naming.directory.Attributes attrs, String name) {
        try {
            javax.naming.directory.Attribute attr = attrs.get(name);
            if (attr != null) {
                return (String) attr.get();
            }
        } catch (Exception e) {
            // Attribute not found or error
        }
        return null;
    }

    // Helper methods

    private LdapContextSource createContextSource(Map<String, String> ldapConfig) {
        LdapContextSource contextSource = new LdapContextSource();

        // Get LDAP server URL and port
        String serverUrl = ldapConfig.get("ldap.server.url");
        String serverPort = ldapConfig.get("ldap.server.port");
        
        // Build full URL if port is separate
        String url = serverUrl;
        if (serverUrl != null && serverPort != null && !serverPort.isEmpty()) {
            // Check if URL already contains port
            if (!serverUrl.matches(".*:\\d+$")) {
                url = serverUrl + ":" + serverPort;
            }
        }
        
        // Get base DN from user search base
        String baseDn = ldapConfig.get("ldap.user.search.base");
        
        // Get bind credentials
        String bindDn = ldapConfig.get("ldap.bind.dn");
        String bindPassword = ldapConfig.get("ldap.bind.password");

        log.info("Creating LDAP context: url={}, baseDn={}, bindDn={}", url, baseDn, bindDn);
        
        if (url == null || url.isEmpty()) {
            throw new RuntimeException("LDAP server URL is not configured");
        }

        contextSource.setUrl(url);
        
        if (baseDn != null && !baseDn.isEmpty()) {
            contextSource.setBase(baseDn);
        }

        if (bindDn != null && !bindDn.isEmpty()) {
            contextSource.setUserDn(bindDn);
            contextSource.setPassword(bindPassword);
        }
        
        // Handle Active Directory referrals - ignore continuation references
        contextSource.setReferral("ignore");

        try {
            contextSource.afterPropertiesSet();
        } catch (Exception e) {
            log.error("Failed to create LDAP context: url={}, baseDn={}, bindDn={}", url, baseDn, bindDn, e);
            throw new RuntimeException("Failed to create LDAP context: " + e.getMessage(), e);
        }

        return contextSource;
    }

}
