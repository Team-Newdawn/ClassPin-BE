package com.ohpin.material.controller;

import com.ohpin.material.service.ConversionService;
import com.ohpin.shared.security.Caller;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class ConversionController {
    private final ConversionService service;

    public ConversionController(ConversionService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/api/convert",
            consumes = "application/json",
            produces = "application/x-ndjson")
    ResponseEntity<StreamingResponseBody> convert(
            @AuthenticationPrincipal Caller c,
            @RequestBody com.ohpin.material.dto.ConversionRequest request) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(service.convert(c, request));
    }
}
