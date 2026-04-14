package com.k8s.platform.service.k8s;

import com.k8s.platform.domain.entity.*;
import com.k8s.platform.domain.repository.*;
import com.k8s.platform.dto.response.ExecSessionResponse;
import com.k8s.platform.service.casbin.CasbinPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommandAuditService {

    private final AllowedCommandRepository allowedCommandRepository;
    private final ExecLogRepository execLogRepository;
    private final ExecSessionRepository execSessionRepository;
    private final ExecSessionRecordingRepository execSessionRecordingRepository;
    private final UserRepository userRepository;
    private final CasbinPermissionService casbinPermissionService;

    /**
     * Returns the Casbin RoleTemplate names assigned to this user across any cluster.
     * Used by the terminal welcome banner and command-allow checks.
     */
    public Set<String> getUserRoleNames(String username) {
        return casbinPermissionService.getUserRoleBindings(username)
                .stream()
                .map(b -> b.getV1())
                .collect(Collectors.toSet());
    }

    public boolean isCommandAllowed(User user, String fullCommand) {
        // 1. Superadmin bypass — always allowed
        if (Boolean.TRUE.equals(user.getIsSuperadmin())) {
            return true;
        }

        // 2. Defensive null and empty check
        if (fullCommand == null || fullCommand.isBlank()) {
            return false;
        }

        // 3. Fetch allowed patterns using Casbin role names (new system)
        Set<String> roleNames = getUserRoleNames(user.getUsername());
        List<AllowedCommand> allowedPatterns = roleNames.isEmpty()
                ? List.of()
                : allowedCommandRepository.findByRoleTemplateNameIn(roleNames);

        if (allowedPatterns.isEmpty()) {
            log.debug("No allowed commands defined for user {} (roles: {}), denying: {}",
                    user.getUsername(), roleNames, fullCommand);
            return false;
        }

        // Use a consistent whitespace normalization for validation checks
        String normalizedFull = fullCommand.trim();

        // 2. Split by pipe (|) to handle command sequences
        String[] segments = normalizedFull.split("\\|");

        if (segments.length == 0) {
            return false;
        }

        for (String segment : segments) {
            String trimmedSegment = segment.trim();

            // 3. Block empty segments like "ls | | grep"
            if (trimmedSegment.isEmpty()) {
                log.warn("Empty command segment detected in: {}", normalizedFull);
                return false;
            }

            // 4. Block forbidden separators and shell injection characters (; & | > < ` $
            // ,)
            // Note: pipe is already handled by outer split, but we check for double-pipe ||
            if (containsForbiddenSeparators(trimmedSegment)) {
                log.warn("Forbidden separator detected in segment: '{}'", trimmedSegment);
                return false;
            }

            // 5. Validate segment against authorized patterns
            boolean segmentAllowed = false;
            String[] parts = trimmedSegment.split("\\s+");
            String baseCmd = (parts.length > 0) ? parts[0] : "";

            for (AllowedCommand allowed : allowedPatterns) {
                String pattern = allowed.getCommandPattern();
                try {
                    // Check 1: Full segment against regex (useful for sophisticated patterns)
                    if (Pattern.matches(pattern, trimmedSegment)) {
                        segmentAllowed = true;
                        break;
                    }

                    // Check 2: Base command against the pattern (useful for simple literal commands
                    // like "ls")
                    if (!baseCmd.isEmpty() && (pattern.equals(baseCmd) || Pattern.matches(pattern, baseCmd))) {
                        segmentAllowed = true;
                        break;
                    }
                } catch (Exception e) {
                    // Fallback to simple literal matching for base command
                    if (!baseCmd.isEmpty() && pattern.equals(baseCmd)) {
                        segmentAllowed = true;
                        break;
                    }
                }
            }

            if (!segmentAllowed) {
                log.warn("Unauthorized command segment: '{}' for user {}", trimmedSegment, user.getUsername());
                return false;
            }
        }

        return true;
    }

    private boolean containsForbiddenSeparators(String segment) {
        // Block command concatenation, redirection and substitution
        // Respecting user's request to specifically block ; and ,
        // & also covers &&, | also covers || inside the segment
        return segment.contains(";") ||
                segment.contains(",") ||
                segment.contains("&") ||
                segment.contains(">") ||
                segment.contains("<") ||
                segment.contains("`") ||
                segment.contains("$(") ||
                segment.contains("||");
    }

    @Transactional
    public void logCommand(String username, String clusterId, String namespace, String podName,
            String containerName, String command, boolean isAllowed) {
        userRepository.findByUsername(username).ifPresent(user -> {
            ExecLog execLog = ExecLog.builder()
                    .user(user)
                    .clusterId(clusterId)
                    .namespace(namespace)
                    .podName(podName)
                    .containerName(containerName)
                    .command(command)
                    .isAllowed(isAllowed)
                    .build();
            execLogRepository.save(execLog);
        });
    }

    @Transactional
    public void saveRecordingChunk(String sessionUid, String eventData) {
        ExecSessionRecording recording = ExecSessionRecording.builder()
                .sessionUid(sessionUid)
                .eventData(eventData)
                .build();
        execSessionRecordingRepository.save(recording);
    }

    @Transactional
    public void createSession(String username, String sessionId, String clusterId, String namespace, String podName) {
        userRepository.findByUsername(username).ifPresent(user -> {
            ExecSession session = ExecSession.builder()
                    .user(user)
                    .sessionId(sessionId)
                    .clusterId(clusterId)
                    .namespace(namespace)
                    .podName(podName)
                    .status("ACTIVE")
                    .build();
            execSessionRepository.save(session);
        });
    }

    @Transactional
    public void closeSession(String sessionId) {
        execSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setStatus("COMPLETED");
            execSessionRepository.save(session);
        });
    }

    public List<ExecSessionResponse> getAllSessions() {
        return execSessionRepository.findAll().stream()
                .map(session -> ExecSessionResponse.builder()
                        .id(session.getId())
                        .sessionId(session.getSessionId())
                        .clusterId(session.getClusterId())
                        .namespace(session.getNamespace())
                        .podName(session.getPodName())
                        .status(session.getStatus())
                        .createdAt(session.getCreatedAt())
                        .userId(session.getUser().getId())
                        .username(session.getUser().getUsername())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<ExecSessionRecording> getSessionRecordings(String sessionId) {
        return execSessionRecordingRepository.findBySessionUidOrderByCreatedAtAsc(sessionId);
    }
}
