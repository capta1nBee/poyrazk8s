package com.k8s.platform.service.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.entity.UserPolicy;
import com.k8s.platform.domain.entity.Page;
import com.k8s.platform.domain.repository.PageRepository;
import com.k8s.platform.domain.repository.UserPolicyRepository;
import com.k8s.platform.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UIPermissionService {

    // All available features in the system
    private static final Set<String> ALL_FEATURES = Set.of(
            "create", "edit", "delete", "exec", "logs", "portforward", "admin", "scale", "restart"
    );

    private final UserPolicyRepository userPolicyRepository;
    private final UserRepository userRepository;
    private final PageRepository pageRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Get all active pages from database
     */
    private Set<String> getAllPagesFromDB() {
        return pageRepository.findByIsActiveTrue().stream()
                .map(Page::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Get all pages a user can view
     */
    public Set<String> getUserPages(String username) {
        User user = userRepository.findByUsername(username).orElse(null);

        // SUPERADMIN can see all pages from database
        if (user != null && user.getIsSuperadmin()) {
            return getAllPagesFromDB();
        }

        Set<String> pages = new HashSet<>();

        // Get pages from user policies
        List<UserPolicy> policies = userPolicyRepository.findBySubjectNameAndIsActiveTrue(username);
        for (UserPolicy policy : policies) {
            try {
                if (policy.getUiPermissionsJson() != null) {
                    Map<String, Object> uiPerms = objectMapper.readValue(
                            policy.getUiPermissionsJson(),
                            new TypeReference<Map<String, Object>>() {
                            });

                    @SuppressWarnings("unchecked")
                    List<String> policyPages = (List<String>) uiPerms.get("pages");
                    if (policyPages != null) {
                        pages.addAll(policyPages);
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing UI permissions for policy {}: {}", policy.getId(), e.getMessage());
            }
        }

        return pages;
    }

    /**
     * Check if user can view a specific page
     */
    public boolean canViewPage(String username, String pagePath) {
        User user = userRepository.findByUsername(username).orElse(null);

        // SUPERADMIN can view all pages
        if (user != null && user.getIsSuperadmin()) {
            return true;
        }

        Set<String> userPages = getUserPages(username);

        // Check exact match
        if (userPages.contains(pagePath)) {
            return true;
        }

        // Check hierarchical match (e.g., if user has 'cluster', they can view
        // 'cluster.list')
        for (String allowedPage : userPages) {
            if (pagePath.startsWith(allowedPage + ".")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if user can edit a specific page
     */
    public boolean canEditPage(String username, String pagePath) {
        User user = userRepository.findByUsername(username).orElse(null);

        // SUPERADMIN can edit all pages
        if (user != null && user.getIsSuperadmin()) {
            return true;
        }

        // Check if user has edit feature
        List<String> features = getUserFeatures(username);
        return features.contains("edit");
    }

    /**
     * Get all page permissions for a user
     */
    public Map<String, Map<String, Boolean>> getAllPagePermissions(String username) {
        Map<String, Map<String, Boolean>> permissions = new HashMap<>();

        Set<String> viewablePages = getUserPages(username);
        boolean canEdit = canEditPage(username, "");

        for (String page : viewablePages) {
            Map<String, Boolean> pagePerms = new HashMap<>();
            pagePerms.put("canView", true);
            pagePerms.put("canEdit", canEdit);
            permissions.put(page, pagePerms);
        }

        return permissions;
    }

    /**
     * Get all features a user has access to
     */
    public List<String> getUserFeatures(String username) {
        User user = userRepository.findByUsername(username).orElse(null);

        // SUPERADMIN has all features
        if (user != null && user.getIsSuperadmin()) {
            return new ArrayList<>(ALL_FEATURES);
        }

        List<String> features = new ArrayList<>();

        // Get features from user policies
        List<UserPolicy> policies = userPolicyRepository.findBySubjectNameAndIsActiveTrue(username);
        for (UserPolicy policy : policies) {
            try {
                if (policy.getUiPermissionsJson() != null) {
                    Map<String, Object> uiPerms = objectMapper.readValue(
                            policy.getUiPermissionsJson(),
                            new TypeReference<Map<String, Object>>() {
                            });

                    @SuppressWarnings("unchecked")
                    List<String> policyFeatures = (List<String>) uiPerms.get("features");
                    if (policyFeatures != null) {
                        features.addAll(policyFeatures);
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing UI features for policy {}: {}", policy.getId(), e.getMessage());
            }
        }

        return features.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Get all available pages from database
     */
    public Set<String> getAllPages() {
        return getAllPagesFromDB();
    }

    /**
     * Get all available features
     */
    public Set<String> getAllFeatures() {
        return ALL_FEATURES;
    }
}
