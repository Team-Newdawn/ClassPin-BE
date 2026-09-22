package com.ohpin.material.service;

import com.ohpin.material.entity.SourcePath;
import com.ohpin.shared.api.ApiFailure;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class DemoConversionService {
  private final DocumentConverter converter;
  private final Semaphore slots = new Semaphore(1);
  private final Path root = Path.of(System.getProperty("java.io.tmpdir"), "ohpin-demo");

  public DemoConversionService(DocumentConverter converter) {
    this.converter = converter;
  }

  public List<Map<String, Object>> convert(InputStream input, String name, String baseUrl)
      throws IOException {
    String extension = SourcePath.extension(name);
    if (!slots.tryAcquire()) throw new ApiFailure(429, "A demo conversion is already running");
    Path work = null, output = null;
    try {
      work = Files.createTempDirectory("ohpin-demo-work-");
      Path source = work.resolve("source" + extension);
      try (var stream = Files.newOutputStream(source)) {
        byte[] buffer = new byte[64 * 1024];
        int read;
        long total = 0;
        while ((read = input.read(buffer)) != -1) {
          total += read;
          if (total > SourcePath.MAX_BYTES) throw new ApiFailure(413, "Source exceeds 1 GiB");
          stream.write(buffer, 0, read);
        }
      }
      Path pdf = converter.toPdf(source, work);
      int count = converter.pageCount(pdf, work);
      UUID job = UUID.randomUUID();
      output = root.resolve(job.toString());
      Files.createDirectories(output);
      var slides = new ArrayList<Map<String, Object>>();
      String url = baseUrl + "/api/demo/files/" + job + "/";
      if (extension.equals(".pdf")) {
        Files.copy(pdf, output.resolve("source.pdf"));
        for (int i = 0; i < count; i++)
          slides.add(
              Map.of(
                  "id",
                  UUID.randomUUID(),
                  "pageIndex",
                  i,
                  "sourcePageIndex",
                  i,
                  "title",
                  "Slide " + (i + 1),
                  "pdfUrl",
                  url + "source.pdf"));
      } else {
        Path destination = output;
        int[] index = {0};
        converter.render(
            pdf,
            work,
            count,
            file -> {
              try {
                Files.move(file, destination.resolve(file.getFileName()));
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
              slides.add(
                  Map.of(
                      "id",
                      UUID.randomUUID(),
                      "pageIndex",
                      index[0],
                      "title",
                      "Slide " + (++index[0]),
                      "imageUrl",
                      url + file.getFileName()));
            });
      }
      return slides;
    } catch (Exception e) {
      remove(output);
      throw e;
    } finally {
      slots.release();
      remove(work);
    }
  }

  public Path file(UUID job, String name) {
    if (!name.equals("source.pdf") && !name.matches("slide-[0-9]+[.]jpg"))
      throw ApiFailure.missing();
    Path file = root.resolve(job.toString()).resolve(name);
    if (!Files.isRegularFile(file)) throw ApiFailure.missing();
    return file;
  }

  private void remove(Path path) throws IOException {
    if (path != null && Files.exists(path))
      try (var files = Files.walk(path)) {
        for (Path file : files.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(file);
      }
  }
}
