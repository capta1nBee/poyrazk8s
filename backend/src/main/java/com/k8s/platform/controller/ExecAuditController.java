package com.k8s.platform.controller;

import com.k8s.platform.domain.entity.AllowedCommand;
import com.k8s.platform.domain.entity.ExecSessionRecording;
import com.k8s.platform.domain.repository.AllowedCommandRepository;
import com.k8s.platform.dto.request.AllowedCommandRequest;
import com.k8s.platform.dto.request.RecordingRequest;
import com.k8s.platform.dto.response.AllowedCommandResponse;
import com.k8s.platform.dto.response.ExecSessionResponse;
import com.k8s.platform.service.k8s.CommandAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exec")
@RequiredArgsConstructor
public class ExecAuditController {

    private final AllowedCommandRepository allowedCommandRepository;
    private final CommandAuditService auditService;
    private final com.k8s.platform.service.audit.AuditLogService auditLogService;

    @GetMapping("/allowed-commands")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<AllowedCommandResponse>> getAllowedCommands() {
        return ResponseEntity.ok(allowedCommandRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @PostMapping("/allowed-commands")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<AllowedCommandResponse> createAllowedCommand(@RequestBody AllowedCommandRequest request) {
        if (request.getRoleTemplateName() == null || request.getRoleTemplateName().isBlank()) {
            throw new IllegalArgumentException("roleTemplateName is required");
        }

        AllowedCommand command = AllowedCommand.builder()
                .roleTemplateName(request.getRoleTemplateName())
                .commandPattern(request.getCommandPattern())
                .description(request.getDescription())
                .build();

        AllowedCommand saved = allowedCommandRepository.save(command);
        auditLogService.log("exec:config", "create allowed command",
                "Pattern: " + request.getCommandPattern() + " for role: " + request.getRoleTemplateName());
        return ResponseEntity.ok(mapToResponse(saved));
    }

    private AllowedCommandResponse mapToResponse(AllowedCommand command) {
        // Resolve display name: prefer roleTemplateName, fall back to legacy role FK
        String roleName = command.getRoleTemplateName() != null
                ? command.getRoleTemplateName()
                : (command.getRole() != null ? command.getRole().getName() : "—");

        return AllowedCommandResponse.builder()
                .id(command.getId())
                .commandPattern(command.getCommandPattern())
                .description(command.getDescription())
                .role(AllowedCommandResponse.RoleResponse.builder()
                        .name(roleName)
                        .build())
                .build();
    }

    @DeleteMapping("/allowed-commands/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> deleteAllowedCommand(@PathVariable Long id) {
        allowedCommandRepository.deleteById(id);
        auditLogService.log("exec:config", "delete allowed command", "ID: " + id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/recording/{sessionUid}")
    public ResponseEntity<Void> saveRecording(@PathVariable String sessionUid, @RequestBody RecordingRequest request) {
        auditService.saveRecordingChunk(sessionUid, request.getEventData());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<ExecSessionResponse>> getSessions() {
        auditLogService.log("exec:audit", "view sessions", "Listing all recorded exec sessions");
        return ResponseEntity.ok(auditService.getAllSessions());
    }

    @GetMapping("/sessions/{sessionId}/recording")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getSessionRecording(@PathVariable String sessionId) {
        List<ExecSessionRecording> recordings = auditService.getSessionRecordings(sessionId);

        // Combine all event data
        StringBuilder combinedEvents = new StringBuilder("[");
        for (int i = 0; i < recordings.size(); i++) {
            if (i > 0)
                combinedEvents.append(",");
            // Remove surrounding brackets if present
            String eventData = recordings.get(i).getEventData();
            if (eventData.startsWith("[") && eventData.endsWith("]")) {
                eventData = eventData.substring(1, eventData.length() - 1);
            }
            combinedEvents.append(eventData);
        }
        combinedEvents.append("]");

        auditLogService.log("exec:audit", "view recording", "Viewing recording for session: " + sessionId);
        return ResponseEntity.ok(Map.of("eventData", combinedEvents.toString()));
    }
}
