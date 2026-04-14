package com.k8s.platform.service.federation;

import com.k8s.platform.domain.entity.Cluster;
import com.k8s.platform.domain.entity.federation.Federation;
import com.k8s.platform.domain.entity.federation.FederationMember;
import com.k8s.platform.domain.entity.federation.FederationResource;
import com.k8s.platform.dto.request.federation.FederationRequest;
import com.k8s.platform.dto.response.federation.FederationResponse;
import com.k8s.platform.domain.repository.ClusterRepository;
import com.k8s.platform.repository.federation.FederationMemberRepository;
import com.k8s.platform.repository.federation.FederationRepository;
import com.k8s.platform.repository.federation.FederationResourceRepository;
import com.k8s.platform.service.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FederationService {

        private final FederationRepository federationRepository;
        private final FederationMemberRepository federationMemberRepository;
        private final FederationResourceRepository federationResourceRepository;
        private final ClusterRepository clusterRepository;
        private final AuditLogService auditLogService;

        @Lazy
        private final WatcherFederationService watcherFederationService;

        @Transactional(readOnly = true)
        public List<FederationResponse> getAllFederations() {
                return federationRepository.findAll().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public FederationResponse getFederation(Long id) {
                Federation federation = federationRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Federation not found: " + id));
                return mapToResponse(federation);
        }

        @Transactional
        public FederationResponse createFederation(FederationRequest request) {
                log.info("Creating federation: {}", request.getName());

                Cluster masterCluster = clusterRepository.findById(request.getMasterClusterId())
                                .orElseThrow(() -> new RuntimeException("Master cluster not found"));

                Federation federation = Federation.builder()
                                .name(request.getName())
                                .masterCluster(masterCluster)
                                .status("Pending")
                                .build();

                federation = federationRepository.save(federation);

                for (Long memberId : request.getMemberClusterIds()) {
                        Cluster memberCluster = clusterRepository.findById(memberId)
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Member cluster not found: " + memberId));

                        FederationMember member = FederationMember.builder()
                                        .federation(federation)
                                        .memberCluster(memberCluster)
                                        .build();
                        federationMemberRepository.save(member);
                }

                for (FederationRequest.FederationResourceDto resDto : request.getResources()) {
                        FederationResource resource = FederationResource.builder()
                                        .federation(federation)
                                        .kind(resDto.getKind())
                                        .namespace(resDto.getNamespace())
                                        .name(resDto.getName())
                                        .syncStatus("Pending")
                                        .build();
                        federationResourceRepository.save(resource);
                }

                // Trigger Sync after Transaction Commits to avoid race conditions
                triggerSyncAfterCommit(federation.getId());

                auditLogService.log("FEDERATION_CREATE", "Created federation: " + request.getName());

                return getFederation(federation.getId());
        }

        @Transactional
        public FederationResponse updateFederation(Long id, FederationRequest request) {
                log.info("Updating federation: {}", id);
                Federation federation = federationRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Federation not found: " + id));

                Cluster masterCluster = clusterRepository.findById(request.getMasterClusterId())
                                .orElseThrow(() -> new RuntimeException("Master cluster not found"));

                federation.setName(request.getName());
                federation.setMasterCluster(masterCluster);
                federation.setStatus("Pending");
                federationRepository.save(federation);

                List<FederationMember> existingMembers = federationMemberRepository.findByFederationId(id);
                federationMemberRepository.deleteAll(existingMembers);

                for (Long memberId : request.getMemberClusterIds()) {
                        Cluster memberCluster = clusterRepository.findById(memberId)
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Member cluster not found: " + memberId));

                        FederationMember member = FederationMember.builder()
                                        .federation(federation)
                                        .memberCluster(memberCluster)
                                        .build();
                        federationMemberRepository.save(member);
                }

                List<FederationResource> existingResources = federationResourceRepository.findByFederationId(id);
                federationResourceRepository.deleteAll(existingResources);

                for (FederationRequest.FederationResourceDto resDto : request.getResources()) {
                        FederationResource resource = FederationResource.builder()
                                        .federation(federation)
                                        .kind(resDto.getKind())
                                        .namespace(resDto.getNamespace())
                                        .name(resDto.getName())
                                        .syncStatus("Pending")
                                        .build();
                        federationResourceRepository.save(resource);
                }

                triggerSyncAfterCommit(federation.getId());

                auditLogService.log("FEDERATION_UPDATE", "Updated federation: " + request.getName());

                return getFederation(federation.getId());
        }

        @Transactional(readOnly = true)
        public void syncFederation(Long id) {
                Federation federation = federationRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Federation not found: " + id));
                watcherFederationService.triggerSync(federation.getId());
        }

        private void triggerSyncAfterCommit(Long federationId) {
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                        log.info("Releasing async sync for federation: {} after transaction commit",
                                                        federationId);
                                        watcherFederationService.triggerSync(federationId);
                                }
                        });
                } else {
                        watcherFederationService.triggerSync(federationId);
                }
        }

        @Transactional
        public void deleteFederation(Long id, boolean removeFromMembers, boolean removeFromMaster) {
                Federation federation = federationRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Federation not found: " + id));

                List<FederationResource> resources = federationResourceRepository.findByFederationId(id);
                for (FederationResource resource : resources) {
                        try {
                                watcherFederationService.deleteFederatedResourceCompletely(resource, removeFromMembers, removeFromMaster);
                        } catch (Exception e) {
                                log.error("Failed to remove resource {} from clusters: {}", resource.getName(), e.getMessage());
                        }
                }

                federationRepository.delete(federation);
                auditLogService.log("FEDERATION_DELETE", "Deleted federation: " + federation.getName() 
                                + " (removeFromMembers=" + removeFromMembers + ", removeFromMaster=" + removeFromMaster + ")");
                log.info("Deleted federation: {} (Members clean: {}, Master clean: {})", id, removeFromMembers, removeFromMaster);
        }

        @Transactional
        public void rollbackResource(Long federationId, Long resourceId) {
                watcherFederationService.rollbackResource(federationId, resourceId);
        }

        private FederationResponse mapToResponse(Federation federation) {
                List<FederationMember> members = federationMemberRepository.findByFederationId(federation.getId());
                List<FederationResource> resources = federationResourceRepository
                                .findByFederationId(federation.getId());

                List<FederationResponse.MemberClusterDto> memberDtos = members.stream()
                                .map(m -> FederationResponse.MemberClusterDto.builder()
                                                .id(m.getId())
                                                .clusterId(m.getMemberCluster().getId())
                                                .clusterName(m.getMemberCluster().getName())
                                                .build())
                                .collect(Collectors.toList());

                List<FederationResponse.ResourceDto> resourceDtos = resources.stream()
                                .map(r -> FederationResponse.ResourceDto.builder()
                                                .id(r.getId())
                                                .kind(r.getKind())
                                                .namespace(r.getNamespace())
                                                .name(r.getName())
                                                .syncStatus(r.getSyncStatus())
                                                .errorMessage(r.getErrorMessage())
                                                .lastErrorTime(r.getLastErrorTime())
                                                .lastSyncTime(r.getLastSyncTime())
                                                .previousStateYaml(r.getPreviousStateYaml())
                                                .dependencyStatus(r.getDependencyStatus())
                                                .build())
                                .collect(Collectors.toList());

                return FederationResponse.builder()
                                .id(federation.getId())
                                .name(federation.getName())
                                .masterClusterId(federation.getMasterCluster().getId())
                                .masterClusterName(federation.getMasterCluster().getName())
                                .status(federation.getStatus())
                                .createdAt(federation.getCreatedAt())
                                .updatedAt(federation.getUpdatedAt())
                                .members(memberDtos)
                                .resources(resourceDtos)
                                .build();
        }
}
