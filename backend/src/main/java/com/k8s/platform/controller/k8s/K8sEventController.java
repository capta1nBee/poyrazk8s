package com.k8s.platform.controller.k8s;

import com.k8s.platform.dto.response.EventResponseDTO;
import com.k8s.platform.service.k8s.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/k8s/{clusterUid}")
@RequiredArgsConstructor
public class K8sEventController {

        private final EventService clusterEventService;
        private final com.k8s.platform.security.ResourceAuthorizationHelper authHelper;

        @GetMapping("/events")

        public ResponseEntity<List<EventResponseDTO>> listAllEvents(@PathVariable String clusterUid) {
                authHelper.checkPermissionOrThrow(clusterUid, "*", "Event", "*", "view");
                List<EventResponseDTO> all = clusterEventService.getAllEvents(clusterUid).stream()
                                .map(EventResponseDTO::fromEntity)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Event", "view",
                                EventResponseDTO::getNamespace, EventResponseDTO::getInvolvedObjectName));
        }

        @GetMapping("/namespaces/{namespace}/events")
        public ResponseEntity<List<EventResponseDTO>> listEvents(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace) {
                authHelper.checkPermissionOrThrow(clusterUid, namespace, "Event", "*", "view");
                List<EventResponseDTO> all = clusterEventService.getEvents(clusterUid, namespace).stream()
                                .map(EventResponseDTO::fromEntity)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(authHelper.filterAccessibleResources(all, clusterUid, "Event", "view",
                                EventResponseDTO::getNamespace, EventResponseDTO::getInvolvedObjectName));
        }

        @GetMapping("/namespaces/{namespace}/resources/{kind}/{name}/events")
        public ResponseEntity<List<EventResponseDTO>> listResourceEvents(
                        @PathVariable String clusterUid,
                        @PathVariable String namespace,
                        @PathVariable String kind,
                        @PathVariable String name) {
                // We check the "Events" action permission on the TARGET resource kind (e.g.,
                // Pod)
                authHelper.checkPermissionOrThrow(clusterUid, namespace, kind, name, "events");

                List<EventResponseDTO> all = clusterEventService.getEventsForResource(clusterUid, namespace, kind, name)
                                .stream()
                                .map(EventResponseDTO::fromEntity)
                                .collect(Collectors.toList());

                return ResponseEntity.ok(all);
        }
}
