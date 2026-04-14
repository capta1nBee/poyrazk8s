package com.k8s.platform.service;

import com.k8s.platform.domain.entity.Action;
import com.k8s.platform.domain.entity.Page;
import com.k8s.platform.domain.repository.ActionRepository;
import com.k8s.platform.domain.repository.PageRepository;
import com.k8s.platform.dto.request.ActionRequest;
import com.k8s.platform.dto.request.PageRequest;
import com.k8s.platform.dto.response.ActionResponse;
import com.k8s.platform.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PageActionService {

    private final PageRepository pageRepository;
    private final ActionRepository actionRepository;

    // Page operations
    public List<PageResponse> getAllPages() {
        return pageRepository.findAll().stream()
                .map(page -> {
                    PageResponse response = PageResponse.fromEntity(page);
                    List<ActionResponse> actions = actionRepository.findByPageIdAndIsActiveTrue(page.getId())
                            .stream()
                            .map(ActionResponse::fromEntity)
                            .collect(Collectors.toList());
                    response.setActions(actions);
                    return response;
                })
                .collect(Collectors.toList());
    }

    public List<PageResponse> getActivePages() {
        return pageRepository.findByIsActiveTrue().stream()
                .map(page -> {
                    PageResponse response = PageResponse.fromEntity(page);
                    List<ActionResponse> actions = actionRepository.findByPageIdAndIsActiveTrue(page.getId())
                            .stream()
                            .map(ActionResponse::fromEntity)
                            .collect(Collectors.toList());
                    response.setActions(actions);
                    return response;
                })
                .collect(Collectors.toList());
    }

    public PageResponse getPageById(Long id) {
        Page page = pageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found: " + id));
        PageResponse response = PageResponse.fromEntity(page);
        List<ActionResponse> actions = actionRepository.findByPageIdAndIsActiveTrue(page.getId())
                .stream()
                .map(ActionResponse::fromEntity)
                .collect(Collectors.toList());
        response.setActions(actions);
        return response;
    }

    public PageResponse getPageByName(String name) {
        Page page = pageRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Page not found: " + name));
        PageResponse response = PageResponse.fromEntity(page);
        List<ActionResponse> actions = actionRepository.findByPageIdAndIsActiveTrue(page.getId())
                .stream()
                .map(ActionResponse::fromEntity)
                .collect(Collectors.toList());
        response.setActions(actions);
        return response;
    }

    @Transactional
    public PageResponse createPage(PageRequest request) {
        if (pageRepository.findByName(request.getName()).isPresent()) {
            throw new RuntimeException("Page with name already exists: " + request.getName());
        }

        Page page = Page.builder()
                .name(request.getName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .resourceKind(request.getResourceKind())
                .icon(request.getIcon())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .isNamespaceScoped(request.getIsNamespaceScoped())
                .pageTier(request.getPageTier())
                .build();

        page = pageRepository.save(page);
        return PageResponse.fromEntity(page);
    }

    @Transactional
    public PageResponse updatePage(Long id, PageRequest request) {
        Page page = pageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found: " + id));

        if (request.getDisplayName() != null) {
            page.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            page.setDescription(request.getDescription());
        }
        if (request.getResourceKind() != null) {
            page.setResourceKind(request.getResourceKind());
        }
        if (request.getIcon() != null) {
            page.setIcon(request.getIcon());
        }
        if (request.getIsActive() != null) {
            page.setIsActive(request.getIsActive());
        }
        if (request.getIsNamespaceScoped() != null) {
            page.setIsNamespaceScoped(request.getIsNamespaceScoped());
        }
        if (request.getPageTier() != null) {
            page.setPageTier(request.getPageTier());
        }

        page = pageRepository.save(page);
        return PageResponse.fromEntity(page);
    }

    @Transactional
    public void deletePage(Long id) {
        Page page = pageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found: " + id));
        pageRepository.delete(page);
    }

    // Action operations
    public List<ActionResponse> getActionsByPageId(Long pageId) {
        return actionRepository.findByPageIdAndIsActiveTrue(pageId)
                .stream()
                .map(ActionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ActionResponse> getActionsByResourceKind(String resourceKind) {
        return actionRepository.findByResourceKindAndIsActiveTrue(resourceKind)
                .stream()
                .map(ActionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ActionResponse> getActionsByActionCode(String actionCode) {
        return actionRepository.findByActionCodeAndIsActiveTrue(actionCode)
                .stream()
                .map(ActionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public ActionResponse getActionById(Long id) {
        Action action = actionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Action not found: " + id));
        return ActionResponse.fromEntity(action);
    }

    @Transactional
    public ActionResponse createAction(ActionRequest request) {
        Page page = pageRepository.findById(request.getPageId())
                .orElseThrow(() -> new RuntimeException("Page not found: " + request.getPageId()));

        if (actionRepository.findByPageIdAndActionCode(request.getPageId(), request.getActionCode()).isPresent()) {
            throw new RuntimeException("Action with code already exists for this page: " + request.getActionCode());
        }

        Action action = Action.builder()
                .page(page)
                .name(request.getName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .actionCode(request.getActionCode())
                .resourceKind(request.getResourceKind())
                .requiresWrite(request.getRequiresWrite() != null ? request.getRequiresWrite() : false)
                .isDangerous(request.getIsDangerous() != null ? request.getIsDangerous() : false)
                .icon(request.getIcon())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        action = actionRepository.save(action);
        return ActionResponse.fromEntity(action);
    }

    @Transactional
    public ActionResponse updateAction(Long id, ActionRequest request) {
        Action action = actionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Action not found: " + id));

        if (request.getDisplayName() != null) {
            action.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            action.setDescription(request.getDescription());
        }
        if (request.getResourceKind() != null) {
            action.setResourceKind(request.getResourceKind());
        }
        if (request.getRequiresWrite() != null) {
            action.setRequiresWrite(request.getRequiresWrite());
        }
        if (request.getIsDangerous() != null) {
            action.setIsDangerous(request.getIsDangerous());
        }
        if (request.getIcon() != null) {
            action.setIcon(request.getIcon());
        }
        if (request.getIsActive() != null) {
            action.setIsActive(request.getIsActive());
        }

        action = actionRepository.save(action);
        return ActionResponse.fromEntity(action);
    }

    @Transactional
    public void deleteAction(Long id) {
        Action action = actionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Action not found: " + id));
        actionRepository.delete(action);
    }
}
