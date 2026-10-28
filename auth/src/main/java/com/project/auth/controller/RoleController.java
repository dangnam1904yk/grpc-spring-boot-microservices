package com.project.auth.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.project.auth.dto.AuthzDtos.CreateRoleRequest;
import com.project.auth.dto.AuthzDtos.RoleDetailResponse;
import com.project.auth.dto.AuthzDtos.RoleResponse;
import com.project.auth.dto.AuthzDtos.UpdateRoleMenusRequest;
import com.project.auth.dto.AuthzDtos.UpdateRolePermissionsRequest;
import com.project.auth.dto.AuthzDtos.UpdateRoleRequest;
import com.project.auth.entity.Role;
import com.project.auth.service.RoleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /** Danh sách kèm số tài khoản / số quyền / số menu của mỗi role. */
    @GetMapping
    public List<RoleResponse> list() {
        return roleService.list().stream().map(r -> RoleResponse.from(r, roleService.countUsers(r))).toList();
    }

    /** Chi tiết: quyền (ô ma trận), menu và user thuộc role. */
    @GetMapping("/{id}")
    public RoleDetailResponse get(@PathVariable UUID id) {
        return detail(roleService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDetailResponse create(@RequestBody @Valid CreateRoleRequest req) {
        return detail(roleService.create(req));
    }

    @PutMapping("/{id}")
    public RoleDetailResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateRoleRequest req) {
        return detail(roleService.update(id, req));
    }

    /** Lưu một hàng của ma trận phân quyền. */
    @PutMapping("/{id}/permissions")
    public RoleDetailResponse updatePermissions(@PathVariable UUID id,
            @RequestBody @Valid UpdateRolePermissionsRequest req) {
        return detail(roleService.updatePermissions(id, req.permissions(), req.version()));
    }

    @PutMapping("/{id}/menus")
    public RoleDetailResponse updateMenus(@PathVariable UUID id, @RequestBody @Valid UpdateRoleMenusRequest req) {
        return detail(roleService.updateMenus(id, req.menuIds(), req.version()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        roleService.delete(id);
    }

    private RoleDetailResponse detail(Role role) {
        return RoleDetailResponse.from(role, roleService.userIdsOf(role));
    }
}
