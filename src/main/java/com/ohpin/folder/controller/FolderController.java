package com.ohpin.folder.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.folder.dto.CreateFolderRequest;
import com.ohpin.folder.dto.UpdateFolderRequest;
import com.ohpin.folder.service.FolderService;
import com.ohpin.shared.security.Caller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/instructor/folders")
public class FolderController {
    private final FolderService service;

    public FolderController(FolderService service) {
        this.service = service;
    }

    @GetMapping
    JsonNode list(@AuthenticationPrincipal Caller c) {
        return service.list(c);
    }

    @PostMapping
    JsonNode create(@AuthenticationPrincipal Caller c, @RequestBody CreateFolderRequest f) {
        return service.create(c, f.toEntity());
    }

    @PatchMapping("/{id}")
    void update(
            @AuthenticationPrincipal Caller c,
            @PathVariable UUID id,
            @RequestBody UpdateFolderRequest body) {
        service.update(c, id, body.toFields());
    }

    @DeleteMapping("/{id}")
    void delete(@AuthenticationPrincipal Caller c, @PathVariable UUID id) {
        service.delete(c, id);
    }
}
