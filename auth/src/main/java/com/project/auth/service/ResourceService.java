package com.project.auth.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthzDtos.CreateResourceRequest;
import com.project.auth.dto.AuthzDtos.UpdateResourceRequest;
import com.project.auth.entity.Action;
import com.project.auth.entity.Resource;
import com.project.auth.entity.Role;
import com.project.auth.repository.ActionRepository;
import com.project.auth.repository.ResourceRepository;
import com.project.auth.repository.RoleRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

/**
 * Resource và các action của nó. Bỏ action khỏi resource / xoá resource thì quyền tương ứng
 * cũng bị gỡ khỏi mọi role, để role không giữ quyền không còn tồn tại.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ActionRepository actionRepository;
    private final RoleRepository roleRepository;

    public List<Resource> list() {
        CurrentUser.requirePermission(Permissions.RESOURCE_READ);
        return resourceRepository.findAllByOrderByCodeAsc();
    }

    public Resource get(UUID id) {
        CurrentUser.requirePermission(Permissions.RESOURCE_READ);
        return getById(id);
    }

    @Transactional
    public Resource create(CreateResourceRequest req) {
        CurrentUser.requirePermission(Permissions.RESOURCE_CREATE);
        if (resourceRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Resource code=" + req.code() + " đã tồn tại");
        }
        requireActionsExist(req.actions());
        Resource resource = new Resource();
        resource.setCode(req.code());
        resource.setName(req.name().trim());
        resource.setDescription(AuthzChecks.trimToNull(req.description()));
        resource.setActions(new HashSet<>(req.actions()));
        try {
            return resourceRepository.saveAndFlush(resource);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Resource code=" + req.code() + " đã tồn tại");
        }
    }

    @Transactional
    public Resource update(UUID id, UpdateResourceRequest req) {
        CurrentUser.requirePermission(Permissions.RESOURCE_UPDATE);
        Resource resource = getById(id);
        AuthzChecks.requireVersion(resource.getVersion(), req.version(), "Resource");
        requireActionsExist(req.actions());

        Set<String> removed = new HashSet<>(resource.getActions());
        removed.removeAll(req.actions());
        revokeFromRoles(permissionsOf(resource.getCode(), removed));

        resource.setName(req.name().trim());
        resource.setDescription(AuthzChecks.trimToNull(req.description()));
        resource.setActions(new HashSet<>(req.actions()));
        return resourceRepository.saveAndFlush(resource);
    }

    @Transactional
    public void delete(UUID id) {
        CurrentUser.requirePermission(Permissions.RESOURCE_DELETE);
        Resource resource = getById(id);
        revokeFromRoles(permissionsOf(resource.getCode(), resource.getActions()));
        resourceRepository.delete(resource);
    }

    private void requireActionsExist(Set<String> actions) {
        Set<String> known = actionRepository.findAll().stream().map(Action::getCode).collect(Collectors.toSet());
        AuthzChecks.requireKnown(actions, known, "Action");
    }

    private void revokeFromRoles(Set<String> permissions) {
        if (permissions.isEmpty()) {
            return;
        }
        for (Role role : roleRepository.findHavingAnyPermission(permissions)) {
            role.getPermissions().removeAll(permissions);
        }
    }

    private static Set<String> permissionsOf(String resource, Set<String> actions) {
        return actions.stream().map(action -> Permissions.of(resource, action)).collect(Collectors.toSet());
    }

    private Resource getById(UUID id) {
        return resourceRepository.findById(id).orElseThrow(() -> AuthzChecks.notFound("Resource", id));
    }
}
