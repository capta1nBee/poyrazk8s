package com.k8s.platform.service.appcreator;

import com.k8s.platform.domain.dto.appcreator.AppCreatorTemplateCreateRequest;
import com.k8s.platform.domain.dto.appcreator.AppCreatorTemplateDto;
import com.k8s.platform.domain.entity.appcreator.AppCreatorTemplate;
import com.k8s.platform.repository.appcreator.AppCreatorTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppCreatorTemplateService {

    private final AppCreatorTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public List<AppCreatorTemplateDto> listAvailable(String clusterUid) {
        return templateRepository.findAvailableForCluster(clusterUid)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppCreatorTemplateDto getTemplate(UUID id) {
        return templateRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
    }

    @Transactional
    public AppCreatorTemplateDto createTemplate(String clusterUid, AppCreatorTemplateCreateRequest req, Long userId) {
        AppCreatorTemplate template = AppCreatorTemplate.builder()
                .clusterUid(clusterUid)
                .name(req.getName())
                .description(req.getDescription())
                .category(req.getCategory())
                .icon(req.getIcon())
                .config(req.getConfig())
                .isPublic(req.getIsPublic() != null ? req.getIsPublic() : false)
                .createdBy(userId)
                .build();
        return toDto(templateRepository.save(template));
    }

    @Transactional
    public AppCreatorTemplateDto updateTemplate(UUID id, AppCreatorTemplateCreateRequest req) {
        AppCreatorTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        template.setName(req.getName());
        template.setDescription(req.getDescription());
        template.setCategory(req.getCategory());
        template.setIcon(req.getIcon());
        template.setConfig(req.getConfig());
        if (req.getIsPublic() != null) template.setIsPublic(req.getIsPublic());
        return toDto(templateRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        templateRepository.deleteById(id);
    }

    private AppCreatorTemplateDto toDto(AppCreatorTemplate e) {
        return AppCreatorTemplateDto.builder()
                .id(e.getId()).clusterUid(e.getClusterUid()).name(e.getName())
                .description(e.getDescription()).category(e.getCategory())
                .icon(e.getIcon()).config(e.getConfig()).isPublic(e.getIsPublic())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }
}

