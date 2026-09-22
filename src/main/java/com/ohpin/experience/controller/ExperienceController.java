package com.ohpin.experience.controller;

import com.ohpin.experience.dto.ExperienceRequest;
import com.ohpin.experience.service.ExperienceService;
import com.ohpin.shared.security.Caller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ExperienceController {
    private final ExperienceService repository;

    public ExperienceController(ExperienceService repository) {
        this.repository = repository;
    }

    @PostMapping("/api/participant/experience")
    void submit(@AuthenticationPrincipal Caller c, @RequestBody ExperienceRequest b) {
        repository.submit(c, b.toEntity());
    }
}
