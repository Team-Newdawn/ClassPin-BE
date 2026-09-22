package com.ohpin.material.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohpin.material.dto.ConversionRequest;
import com.ohpin.material.entity.SourcePath;
import com.ohpin.shared.api.ApiFailure;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class ConversionService {
    private final SupabaseGateway storage;
    private final DocumentConverter converter;
    private final ObjectMapper json;
    private final Semaphore capacity;

    public ConversionService(
            SupabaseGateway storage,
            DocumentConverter converter,
            ObjectMapper json,
            @Value("${ohpin.conversion.max-concurrent}") int maxConcurrent) {
        if (maxConcurrent < 1)
            throw new IllegalArgumentException("Conversion concurrency must be positive");
        this.storage = storage;
        this.converter = converter;
        this.json = json;
        this.capacity = new Semaphore(maxConcurrent);
    }

    public StreamingResponseBody convert(Caller caller, ConversionRequest request) {
        String path = SourcePath.validate(caller.id(), request.sourcePath(), request.fileName());
        // Reject before committing the NDJSON response.
        var info =
                storage.json(
                        caller.token(),
                        "GET",
                        "/storage/v1/object/info/course-materials/" + path,
                        Map.of(),
                        null,
                        null);
        long size = info.path("size").asLong(info.path("metadata").path("size").asLong(-1));
        if (size < 0 || size > SourcePath.MAX_BYTES)
            throw new ApiFailure(413, "Source exceeds 1 GiB or has invalid metadata");
        return output -> {
            // Reserve a slot only once streaming actually starts; abandoned responses cannot leak slots.
            if (!capacity.tryAcquire()) {
                emit(
                        output,
                        Map.of("type", "error", "error", "All conversion slots are busy; try again shortly"));
                return;
            }
            Path workspace = null;
            var uploaded = new ArrayList<String>();
            try {
                workspace = Files.createTempDirectory("ohpin-convert-");
                Path source = workspace.resolve("source" + SourcePath.extension(request.fileName()));
                storage.download(caller, "course-materials", path, source, SourcePath.MAX_BYTES);
                Path pdf = converter.toPdf(source, workspace);
                int total = converter.pageCount(pdf, workspace);
                emit(output, Map.of("type", "meta", "total", total));
                if (SourcePath.extension(request.fileName()).equals(".pdf")) {
                    for (int i = 0; i < total; i++)
                        emit(
                                output,
                                Map.of(
                                        "type",
                                        "slide",
                                        "slide",
                                        Map.of(
                                                "id",
                                                UUID.randomUUID(),
                                                "pageIndex",
                                                i,
                                                "title",
                                                "Slide " + (i + 1),
                                                "sourcePageIndex",
                                                i)));
                } else {
                    UUID upload = UUID.randomUUID();
                    int[] page = {0};
                    converter.render(
                            pdf,
                            workspace,
                            total,
                            file -> {
                                UUID id = UUID.randomUUID();
                                String imagePath = caller.id() + "/" + upload + "/" + id + ".jpg";
                                storage.upload(caller, imagePath, file);
                                uploaded.add(imagePath);
                                emit(
                                        output,
                                        Map.of(
                                                "type",
                                                "slide",
                                                "slide",
                                                Map.of(
                                                        "id",
                                                        id,
                                                        "pageIndex",
                                                        page[0],
                                                        "title",
                                                        "Slide " + (++page[0]),
                                                        "imagePath",
                                                        imagePath,
                                                        "imageUrl",
                                                        storage.publicSlideUrl(imagePath))));
                                try {
                                    Files.delete(file);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                }
                emit(output, Map.of("type", "done"));
            } catch (Exception e) {
                if (!uploaded.isEmpty()) {
                    try {
                        storage.removeObjects(caller, "lecture-slides", uploaded);
                    } catch (ApiFailure cleanupError) {
                        for (String imagePath : uploaded)
                            storage.insertOnly(
                                    caller,
                                    "storage_cleanup_jobs",
                                    Map.of(
                                            "owner_id",
                                            caller.id(),
                                            "bucket",
                                            "lecture-slides",
                                            "object_path",
                                            imagePath));
                    }
                }
                try {
                    emit(
                            output,
                            Map.of(
                                    "type",
                                    "error",
                                    "error",
                                    e instanceof ApiFailure ? e.getMessage() : "Document conversion failed"));
                } catch (UncheckedIOException disconnected) {
                    /* Client already disconnected. */
                }
            } finally {
                capacity.release();
                if (workspace != null)
                    try (var files = Files.walk(workspace)) {
                        for (Path file : files.sorted(Comparator.reverseOrder()).toList())
                            Files.deleteIfExists(file);
                    }
            }
        };
    }

    private void emit(OutputStream output, Object event) {
        try {
            output.write(json.writeValueAsBytes(event));
            output.write('\n');
            output.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
