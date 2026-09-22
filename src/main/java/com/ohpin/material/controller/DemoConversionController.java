package com.ohpin.material.controller;

import com.ohpin.material.service.DemoConversionService;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
@RequestMapping("/api/demo")
public class DemoConversionController {
    private final DemoConversionService service;

    public DemoConversionController(DemoConversionService service) {
        this.service = service;
    }

    @PostMapping("/convert")
    Map<String, Object> convert(
            HttpServletRequest request, @RequestHeader("X-File-Name") String fileName)
            throws IOException {
        String base =
                request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        return Map.of(
                "slides",
                service.convert(
                        request.getInputStream(), URLDecoder.decode(fileName, StandardCharsets.UTF_8), base));
    }

    @GetMapping("/files/{job}/{name}")
    ResponseEntity<Resource> file(@PathVariable UUID job, @PathVariable String name) {
        var file = service.file(job, name);
        return ResponseEntity.ok()
                .contentType(name.endsWith(".pdf") ? MediaType.APPLICATION_PDF : MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(file));
    }
}
