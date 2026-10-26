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

import com.project.auth.dto.AuthzDtos.ActionResponse;
import com.project.auth.dto.AuthzDtos.CreateActionRequest;
import com.project.auth.dto.AuthzDtos.UpdateActionRequest;
import com.project.auth.service.ActionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;

    @GetMapping
    public List<ActionResponse> list() {
        return actionService.list().stream().map(ActionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ActionResponse get(@PathVariable UUID id) {
        return ActionResponse.from(actionService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActionResponse create(@RequestBody @Valid CreateActionRequest req) {
        return ActionResponse.from(actionService.create(req));
    }

    @PutMapping("/{id}")
    public ActionResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateActionRequest req) {
        return ActionResponse.from(actionService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        actionService.delete(id);
    }
}
