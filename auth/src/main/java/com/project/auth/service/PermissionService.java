package com.project.auth.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthzDtos.CreatePermissionRequest;
import com.project.auth.dto.AuthzDtos.Status;
import com.project.auth.dto.AuthzDtos.UpdatePermissionRequest;
import com.project.auth.entity.Permission;
import com.project.auth.repository.PermissionRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

/**
 * Danh mục thao tác (VIEW, ADD, EDIT...). Thao tác hệ thống (code đang kiểm tra) không xoá / không tắt được.
 * Xoá thao tác thì quyền tương ứng trên mọi menu cũng bị gỡ khỏi mọi role.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final AuthorityService authorityService;

    public List<Permission> list() {
        CurrentUser.requirePermission(Permissions.PERMISSION_READ);
        return permissionRepository.findAllByOrderByCodeAsc();
    }

    @Transactional
    public Permission create(CreatePermissionRequest req) {
        CurrentUser.requirePermission(Permissions.PERMISSION_CREATE);
        if (permissionRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Mã quyền " + req.code() + " đã tồn tại");
        }
        Permission permission = new Permission();
        permission.setCode(req.code());
        permission.setName(req.name().trim());
        permission.setDescription(AuthzChecks.trimToNull(req.description()));
        try {
            return permissionRepository.saveAndFlush(permission);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Mã quyền " + req.code() + " đã tồn tại");
        }
    }

    @Transactional
    public Permission update(UUID id, UpdatePermissionRequest req) {
        CurrentUser.requirePermission(Permissions.PERMISSION_UPDATE);
        Permission permission = getById(id);
        AuthzChecks.requireVersion(permission.getVersion(), req.version(), "Quyền " + permission.getCode());
        boolean disable = req.status() == Status.INACTIVE;
        if (disable && permission.isBuiltIn()) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT,
                    "Không tắt được quyền hệ thống " + permission.getCode());
        }
        permission.setName(req.name().trim());
        permission.setDescription(AuthzChecks.trimToNull(req.description()));
        if (req.status() != null) {
            permission.setDisabled(disable);
        }
        return permissionRepository.saveAndFlush(permission);
    }

    @Transactional
    public void delete(UUID id) {
        CurrentUser.requirePermission(Permissions.PERMISSION_DELETE);
        Permission permission = getById(id);
        if (permission.isBuiltIn()) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT,
                    "Không xoá được quyền hệ thống " + permission.getCode());
        }
        String suffix = Permissions.SEPARATOR + permission.getCode();
        authorityService.revokeGrants("%" + suffix, grant -> grant.endsWith(suffix));
        permissionRepository.delete(permission);
    }

    private Permission getById(UUID id) {
        return permissionRepository.findById(id).orElseThrow(() -> AuthzChecks.notFound("Quyền", id));
    }
}
