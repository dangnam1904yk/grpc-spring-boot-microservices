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

import com.project.auth.dto.AuthzDtos.CreatePermissionRequest;
import com.project.auth.dto.AuthzDtos.PermissionResponse;
import com.project.auth.dto.AuthzDtos.UpdatePermissionRequest;
import com.project.auth.service.PermissionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Danh mục thao tác (VIEW, ADD, EDIT...): cột của ma trận phân quyền. */
@RestController
@RequestMapping("/api/auth/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    public List<PermissionResponse> list() {
        return permissionService.list().stream().map(PermissionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionResponse create(@RequestBody @Valid CreatePermissionRequest req) {
        return PermissionResponse.from(permissionService.create(req));
    }

    @PutMapping("/{id}")
    public PermissionResponse update(@PathVariable UUID id, @RequestBody @Valid UpdatePermissionRequest req) {
        return PermissionResponse.from(permissionService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        permissionService.delete(id);
    }
}
