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

import com.project.auth.dto.AuthzDtos.CreateMenuRequest;
import com.project.auth.dto.AuthzDtos.MenuNode;
import com.project.auth.dto.AuthzDtos.MenuResponse;
import com.project.auth.dto.AuthzDtos.UpdateMenuRequest;
import com.project.auth.service.MenuService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** Cây menu đầy đủ (kể cả menu đang tắt). */
    @GetMapping
    public List<MenuNode> tree() {
        return menuService.tree();
    }

    @GetMapping("/{id}")
    public MenuResponse get(@PathVariable UUID id) {
        return MenuResponse.from(menuService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuResponse create(@RequestBody @Valid CreateMenuRequest req) {
        return MenuResponse.from(menuService.create(req));
    }

    @PutMapping("/{id}")
    public MenuResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateMenuRequest req) {
        return MenuResponse.from(menuService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        menuService.delete(id);
    }
}
