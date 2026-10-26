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
import com.project.auth.dto.AuthzDtos.RoleResponse;
import com.project.auth.dto.AuthzDtos.UpdateRoleRequest;
import com.project.auth.service.RoleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public List<RoleResponse> list() {
        return roleService.list().stream().map(RoleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RoleResponse get(@PathVariable UUID id) {
        return RoleResponse.from(roleService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@RequestBody @Valid CreateRoleRequest req) {
        return RoleResponse.from(roleService.create(req));
    }

    @PutMapping("/{id}")
    public RoleResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateRoleRequest req) {
        return RoleResponse.from(roleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        roleService.delete(id);
    }
}
