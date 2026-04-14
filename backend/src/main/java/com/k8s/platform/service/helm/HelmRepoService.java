package com.k8s.platform.service.helm;

import com.k8s.platform.domain.dto.helm.HelmRepositoryCreateRequest;
import com.k8s.platform.domain.dto.helm.HelmRepositoryDto;
import com.k8s.platform.domain.entity.helm.HelmRepository;
import com.k8s.platform.repository.helm.HelmRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HelmRepoService {

    private final HelmRepositoryRepository repository;
    private final HelmService helmService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("Synchronizing Helm repositories from database...");
        List<HelmRepository> allRepos = repository.findAll();
        for (HelmRepository repo : allRepos) {
            try {
                helmService.addRepositoryAsync(repo.getName(), repo.getUrl(), repo.getUsername(), repo.getPassword())
                        .thenAccept(res -> log.info("Successfully added helm repo: {}", repo.getName()))
                        .exceptionally(ex -> {
                            log.error("Failed to add helm repo on startup: {}", repo.getName(), ex);
                            return null;
                        });
            } catch (Exception e) {
                log.error("Error triggering helm repo add for {}", repo.getName(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<HelmRepositoryDto> getAllForCluster(String clusterUid) {
        return repository.findAllByClusterUid(clusterUid).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public HelmRepositoryDto createRepository(String clusterUid, HelmRepositoryCreateRequest request) {
        if (repository.findByClusterUidAndName(clusterUid, request.getName()).isPresent()) {
            throw new IllegalArgumentException("Repository with name " + request.getName() + " already exists in this cluster.");
        }

        HelmRepository helmRepository = HelmRepository.builder()
                .clusterUid(clusterUid)
                .name(request.getName())
                .url(request.getUrl())
                .isPrivate(request.isPrivate())
                .username(request.getUsername())
                .password(request.getPassword())
                .build();

        HelmRepository saved = repository.save(helmRepository);
        
        // Sync with Helm CLI
        helmService.addRepositoryAsync(saved.getName(), saved.getUrl(), saved.getUsername(), saved.getPassword())
                .exceptionally(ex -> {
                    log.error("Failed to sync helm repo to CLI: {}", saved.getName(), ex);
                    return null;
                });

        return mapToDto(saved);
    }

    @Transactional
    public HelmRepositoryDto updateRepository(UUID id, String clusterUid, HelmRepositoryCreateRequest request) {
        HelmRepository helmRepository = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Helm repository not found"));

        if (!helmRepository.getClusterUid().equals(clusterUid)) {
            throw new IllegalArgumentException("Helm repository does not belong to this cluster");
        }

        helmRepository.setName(request.getName());
        helmRepository.setUrl(request.getUrl());
        helmRepository.setPrivate(request.isPrivate());
        helmRepository.setUsername(request.getUsername());
        
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            helmRepository.setPassword(request.getPassword());
        }

        HelmRepository saved = repository.save(helmRepository);

        // Sync with Helm CLI
        helmService.addRepositoryAsync(saved.getName(), saved.getUrl(), saved.getUsername(), saved.getPassword())
                .exceptionally(ex -> {
                    log.error("Failed to sync updated helm repo to CLI: {}", saved.getName(), ex);
                    return null;
                });

        return mapToDto(saved);
    }

    @Transactional
    public void deleteRepository(UUID id, String clusterUid) {
        HelmRepository helmRepository = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Helm repository not found"));

        if (!helmRepository.getClusterUid().equals(clusterUid)) {
            throw new IllegalArgumentException("Helm repository does not belong to this cluster");
        }

        repository.delete(helmRepository);
    }
    
    public HelmRepository getEntityByIdAndCluster(UUID id, String clusterUid) {
        HelmRepository helmRepository = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Helm repository not found"));
        if (!helmRepository.getClusterUid().equals(clusterUid)) {
            throw new IllegalArgumentException("Helm repository does not belong to this cluster");
        }
        return helmRepository;
    }

    private HelmRepositoryDto mapToDto(HelmRepository entity) {
        return HelmRepositoryDto.builder()
                .id(entity.getId())
                .clusterUid(entity.getClusterUid())
                .name(entity.getName())
                .url(entity.getUrl())
                .isPrivate(entity.isPrivate())
                .username(entity.getUsername())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
