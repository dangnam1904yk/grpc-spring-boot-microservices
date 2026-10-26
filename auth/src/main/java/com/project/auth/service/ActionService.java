package com.project.auth.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthzDtos.CreateActionRequest;
import com.project.auth.dto.AuthzDtos.UpdateActionRequest;
import com.project.auth.entity.Action;
import com.project.auth.repository.ActionRepository;
import com.project.auth.repository.ResourceRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActionService {

    private final ActionRepository actionRepository;
    private final ResourceRepository resourceRepository;

    public List<Action> list() {
        CurrentUser.requirePermission(Permissions.ACTION_READ);
        return actionRepository.findAllByOrderByCodeAsc();
    }

    public Action get(UUID id) {
        CurrentUser.requirePermission(Permissions.ACTION_READ);
        return getById(id);
    }

    @Transactional
    public Action create(CreateActionRequest req) {
        CurrentUser.requirePermission(Permissions.ACTION_CREATE);
        if (actionRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Action code=" + req.code() + " đã tồn tại");
        }
        Action action = new Action();
        action.setCode(req.code());
        action.setName(req.name().trim());
        action.setDescription(AuthzChecks.trimToNull(req.description()));
        try {
            return actionRepository.saveAndFlush(action);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Action code=" + req.code() + " đã tồn tại");
        }
    }

    @Transactional
    public Action update(UUID id, UpdateActionRequest req) {
        CurrentUser.requirePermission(Permissions.ACTION_UPDATE);
        Action action = getById(id);
        AuthzChecks.requireVersion(action.getVersion(), req.version(), "Action");
        action.setName(req.name().trim());
        action.setDescription(AuthzChecks.trimToNull(req.description()));
        return actionRepository.saveAndFlush(action);
    }

    /** Action đang được resource nào dùng thì phải bỏ khỏi resource đó trước. */
    @Transactional
    public void delete(UUID id) {
        CurrentUser.requirePermission(Permissions.ACTION_DELETE);
        Action action = getById(id);
        List<String> resources = resourceRepository.findCodesUsingAction(action.getCode());
        if (!resources.isEmpty()) {
            throw new AppException(ErrorCode.IN_USE,
                    "Action " + action.getCode() + " đang được dùng bởi resource " + resources);
        }
        actionRepository.delete(action);
    }

    private Action getById(UUID id) {
        return actionRepository.findById(id).orElseThrow(() -> AuthzChecks.notFound("Action", id));
    }
}
