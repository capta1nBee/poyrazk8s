package com.k8s.platform.util;

import com.k8s.platform.domain.entity.User;
import com.k8s.platform.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null)
            return null;

        if (authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        } else if (authentication.getPrincipal() instanceof UserDetails) {
            String username = ((UserDetails) authentication.getPrincipal()).getUsername();
            return userRepository.findByUsername(username).orElse(null);
        } else {
            String username = authentication.getName();
            return userRepository.findByUsername(username).orElse(null);
        }
    }
}
