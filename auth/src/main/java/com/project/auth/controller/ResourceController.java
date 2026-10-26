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

import com.project.auth.dto.AuthzDtos.CreateResourceRequest;
import com.project.auth.dto.AuthzDtos.ResourceResponse;
import com.project.auth.dto.AuthzDtos.UpdateResourceRequest;
import com.project.auth.service.ResourceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping
    public List<ResourceResponse> list() {
        return resourceService.list().stream().map(ResourceResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable UUID id) {
        return ResourceResponse.from(resourceService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse create(@RequestBody @Valid CreateResourceRequest req) {
        return ResourceResponse.from(resourceService.create(req));
    }

    @PutMapping("/{id}")
    public ResourceResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateResourceRequest req) {
        return ResourceResponse.from(resourceService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        resourceService.delete(id);
    }
}
