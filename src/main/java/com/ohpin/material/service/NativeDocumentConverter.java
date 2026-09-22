package com.ohpin.material.service;

import com.ohpin.shared.api.ApiFailure;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NativeDocumentConverter implements DocumentConverter {
  private final String soffice, pdfinfo, pdftoppm;

  public NativeDocumentConverter(
      @Value("${ohpin.conversion.soffice}") String soffice,
      @Value("${ohpin.conversion.pdfinfo}") String pdfinfo,
      @Value("${ohpin.conversion.pdftoppm}") String pdftoppm) {
    this.soffice = soffice;
    this.pdfinfo = pdfinfo;
    this.pdftoppm = pdftoppm;
  }

  public Path toPdf(Path source, Path workspace) {
    if (source.toString().endsWith(".pdf")) return source;
    run(
        List.of(
            soffice,
            "-env:UserInstallation=" + workspace.resolve("office-profile").toUri(),
            "--headless",
            "--convert-to",
            "pdf",
            "--outdir",
            workspace.toString(),
            source.toString()),
        workspace,
        Duration.ofMinutes(15));
    Path pdf = workspace.resolve("source.pdf");
    if (!Files.isRegularFile(pdf))
      throw new ApiFailure(422, "The presentation could not be converted");
    return pdf;
  }

  public int pageCount(Path pdf, Path workspace) {
    String info = run(List.of(pdfinfo, pdf.toString()), workspace, Duration.ofMinutes(1));
    var match = Pattern.compile("(?m)^Pages:\\s+(\\d+)\\s*$").matcher(info);
    if (!match.find()) throw new ApiFailure(422, "Unable to read the PDF page count");
    int count = Integer.parseInt(match.group(1));
    if (count < 1) throw new ApiFailure(422, "The document has no pages");
    return count;
  }

  public void render(Path pdf, Path workspace, int count, Consumer<Path> onPage) {
    long deadline = System.nanoTime() + Duration.ofMinutes(50).toNanos();
    // Render one page at a time so memory/disk stay bounded and progress is immediately available.
    for (int page = 1; page <= count; page++) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) throw new ApiFailure(504, "Document rendering timed out");
      Path prefix = workspace.resolve("slide-" + page);
      run(
          List.of(
              pdftoppm,
              "-f",
              String.valueOf(page),
              "-l",
              String.valueOf(page),
              "-singlefile",
              "-jpeg",
              "-jpegopt",
              "quality=100",
              "-scale-to",
              "3840",
              pdf.toString(),
              prefix.toString()),
          workspace,
          Duration.ofNanos(Math.min(remaining, Duration.ofMinutes(5).toNanos())));
      onPage.accept(workspace.resolve("slide-" + page + ".jpg"));
    }
  }

  private String run(List<String> command, Path workspace, Duration timeout) {
    Process process = null;
    try {
      Path log = workspace.resolve("process.log");
      var builder =
          new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
      builder.environment().put("LC_ALL", "C");
      process = builder.start();
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS))
        throw new ApiFailure(504, "Document conversion timed out");
      if (process.exitValue() != 0) throw new ApiFailure(422, "Document conversion failed");
      // The native output is kept private and bounded when read.
      try (var stream = Files.newInputStream(log)) {
        return new String(stream.readNBytes(64 * 1024), java.nio.charset.StandardCharsets.UTF_8);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiFailure(503, "Conversion interrupted");
    } catch (IOException e) {
      throw new ApiFailure(503, "Document tools are unavailable");
    } finally {
      if (process != null && process.isAlive()) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
      }
    }
  }
}
