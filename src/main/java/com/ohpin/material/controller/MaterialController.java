package com.ohpin.material.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.material.dto.AppendSlidesRequest;
import com.ohpin.material.dto.CreateMaterialRequest;
import com.ohpin.material.dto.SlideNoteRequest;
import com.ohpin.material.service.MaterialService;
import com.ohpin.material.service.StorageCleanupService;
import com.ohpin.shared.security.Caller;

import java.util.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class MaterialController {
    private final MaterialService service;
    private final StorageCleanupService cleanup;

    public MaterialController(MaterialService service, StorageCleanupService cleanup) {
        this.service = service;
        this.cleanup = cleanup;
    }

    @PostMapping("/api/instructor/materials")
    void create(@AuthenticationPrincipal Caller c, @RequestBody CreateMaterialRequest draft) {
        service.create(c, draft.toEntity());
    }

    @GetMapping({"/api/instructor/versions/{id}/slides", "/api/participant/versions/{id}/slides"})
    JsonNode slides(@AuthenticationPrincipal Caller c, @PathVariable UUID id) {
        return service.slides(c, id);
    }

    @PostMapping("/api/instructor/versions/{id}/slides")
    JsonNode append(
            @AuthenticationPrincipal Caller c,
            @PathVariable UUID id,
            @RequestBody AppendSlidesRequest b) {
        return service.append(c, id, b.slides());
    }

    @DeleteMapping("/api/instructor/slides/{id}")
    JsonNode delete(@AuthenticationPrincipal Caller c, @PathVariable UUID id) {
        var deleted = service.deleteSlide(c, id);
        cleanup.retry(c);
        return deleted;
    }

    @PutMapping("/api/instructor/slides/{id}/note")
    void note(
            @AuthenticationPrincipal Caller c, @PathVariable UUID id, @RequestBody SlideNoteRequest b) {
        service.saveNote(c, id, b.body());
    }
}
