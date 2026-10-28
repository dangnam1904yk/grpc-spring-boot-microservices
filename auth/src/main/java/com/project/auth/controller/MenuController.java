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
import com.project.auth.dto.AuthzDtos.MenuOrderRequest;
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
    public MenuNode get(@PathVariable UUID id) {
        return menuService.node(menuService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuNode create(@RequestBody @Valid CreateMenuRequest req) {
        return menuService.node(menuService.create(req));
    }

    @PutMapping("/{id}")
    public MenuNode update(@PathVariable UUID id, @RequestBody @Valid UpdateMenuRequest req) {
        return menuService.node(menuService.update(id, req));
    }

    /** Đổi thứ tự / menu cha của nhiều menu một lần. */
    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@RequestBody @Valid List<@Valid MenuOrderRequest> items) {
        menuService.reorder(items);
    }

    /** Xoá cả menu con / cháu. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        menuService.delete(id);
    }
}
