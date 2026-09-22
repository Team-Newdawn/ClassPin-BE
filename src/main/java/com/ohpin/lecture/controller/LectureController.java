package com.ohpin.lecture.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.lecture.dto.MoveCourseRequest;
import com.ohpin.lecture.service.LectureService;
import com.ohpin.material.service.StorageCleanupService;
import com.ohpin.shared.security.Caller;

import java.util.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class LectureController {
    private final LectureService service;
    private final StorageCleanupService cleanup;

    public LectureController(LectureService service, StorageCleanupService cleanup) {
        this.service = service;
        this.cleanup = cleanup;
    }

    @GetMapping("/api/instructor/courses")
    JsonNode owned(@AuthenticationPrincipal Caller c) {
        return service.owned(c);
    }

    @GetMapping("/api/participant/join/{code}")
    JsonNode join(@AuthenticationPrincipal Caller c, @PathVariable String code) {
        return service.join(c, code);
    }

    @GetMapping("/api/participant/lectures/{id}")
    JsonNode live(@AuthenticationPrincipal Caller c, @PathVariable UUID id) {
        return service.live(c, id);
    }

    @GetMapping({"/api/instructor/lectures/{id}/state", "/api/participant/lectures/{id}/state"})
    JsonNode state(@AuthenticationPrincipal Caller c, @PathVariable UUID id) {
        return service.state(c, id);
    }

    @PatchMapping("/api/instructor/lectures/{id}")
    void update(
            @AuthenticationPrincipal Caller c,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> fields) {
        service.update(c, id, fields);
    }

    @PatchMapping("/api/instructor/courses/{id}/folder")
    void move(
            @AuthenticationPrincipal Caller c,
            @PathVariable UUID id,
            @RequestBody MoveCourseRequest body) {
        service.move(c, id, body.folderId());
    }

    @DeleteMapping("/api/instructor/courses/{id}")
    Map<String, Object> delete(@AuthenticationPrincipal Caller c, @PathVariable UUID id) {
        service.delete(c, id);
        return Map.of("deleted", true, "cleanupPending", !cleanup.retry(c));
    }

    @PostMapping("/api/instructor/storage-cleanup")
    Map<String, Object> retry(@AuthenticationPrincipal Caller c) {
        return Map.of("cleanupPending", !cleanup.retry(c));
    }
}
