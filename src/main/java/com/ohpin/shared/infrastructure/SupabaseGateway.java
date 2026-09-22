package com.ohpin.shared.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.ohpin.shared.api.ApiFailure;
import com.ohpin.shared.security.Caller;
import java.io.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SupabaseGateway {
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final ObjectMapper mapper;
  private final SupabaseProperties properties;

  @org.springframework.beans.factory.annotation.Value("${ohpin.supabase.public-url}")
  private String publicUrl;

  public SupabaseGateway(ObjectMapper mapper, SupabaseProperties properties) {
    this.mapper = mapper;
    this.properties = properties;
  }

  private HttpRequest.Builder request(String token, String path, Map<String, String> query) {
    var uri = UriComponentsBuilder.fromUriString(properties.url()).path(path);
    query.forEach((key, value) -> uri.queryParam(key, "{q" + key.replace('.', '_') + "}"));
    var variables = new HashMap<String, String>();
    query.forEach((key, value) -> variables.put("q" + key.replace('.', '_'), value));
    return HttpRequest.newBuilder(uri.encode().buildAndExpand(variables).toUri())
        .timeout(Duration.ofSeconds(60))
        .header("apikey", properties.publishableKey())
        .header("Authorization", "Bearer " + token);
  }

  public JsonNode json(
      String token,
      String method,
      String path,
      Map<String, String> query,
      Object body,
      String prefer) {
    try {
      var req = request(token, path, query).header("Content-Type", "application/json");
      if (prefer != null) req.header("Prefer", prefer);
      var payload =
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body));
      var res =
          client.send(req.method(method, payload).build(), HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() >= 400) throw failure(res.statusCode());
      return res.body().isBlank() ? mapper.nullNode() : mapper.readTree(res.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiFailure(503, "Upstream request interrupted");
    } catch (IOException e) {
      throw new ApiFailure(502, "Supabase is unavailable");
    }
  }

  public JsonNode get(Caller c, String table, Map<String, String> query) {
    return json(c.token(), "GET", "/rest/v1/" + table, query, null, null);
  }

  public JsonNode insert(Caller c, String table, Object body) {
    return json(c.token(), "POST", "/rest/v1/" + table, Map.of(), body, "return=representation");
  }

  public void insertOnly(Caller c, String table, Object body) {
    json(c.token(), "POST", "/rest/v1/" + table, Map.of(), body, "return=minimal");
  }

  public JsonNode patch(Caller c, String table, Map<String, String> query, Object body) {
    return required(
        json(c.token(), "PATCH", "/rest/v1/" + table, query, body, "return=representation"));
  }

  public JsonNode delete(Caller c, String table, Map<String, String> query) {
    return required(
        json(c.token(), "DELETE", "/rest/v1/" + table, query, null, "return=representation"));
  }

  public JsonNode rpc(Caller c, String name, Object body) {
    return json(c.token(), "POST", "/rest/v1/rpc/" + name, Map.of(), body, null);
  }

  public static JsonNode required(JsonNode rows) {
    if (!rows.isArray() || rows.isEmpty()) throw ApiFailure.missing();
    return rows.get(0);
  }

  public static JsonNode first(JsonNode rows) {
    return rows.isArray() && !rows.isEmpty() ? rows.get(0) : null;
  }

  public void download(
      Caller c, String bucket, String path, java.nio.file.Path destination, long limit) {
    try {
      var res =
          client.send(
              request(
                      c.token(),
                      "/storage/v1/object/authenticated/" + bucket + "/" + path,
                      Map.of())
                  .timeout(Duration.ofMinutes(15))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofInputStream());
      try (var input = res.body()) {
        if (res.statusCode() >= 400) throw failure(res.statusCode());
        try (var output = java.nio.file.Files.newOutputStream(destination)) {
          byte[] buffer = new byte[64 * 1024];
          long bytes = 0;
          int n;
          while ((n = input.read(buffer)) != -1) {
            bytes += n;
            if (bytes > limit) throw new ApiFailure(413, "Source exceeds 1 GiB");
            output.write(buffer, 0, n);
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiFailure(503, "Download interrupted");
    } catch (IOException e) {
      throw new ApiFailure(502, "Source download failed");
    }
  }

  public void upload(Caller c, String path, java.nio.file.Path file) {
    try {
      var res =
          client.send(
              request(c.token(), "/storage/v1/object/lecture-slides/" + path, Map.of())
                  .header("Content-Type", "image/jpeg")
                  .header("x-upsert", "false")
                  .POST(HttpRequest.BodyPublishers.ofFile(file))
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      if (res.statusCode() >= 400) throw failure(res.statusCode());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiFailure(503, "Upload interrupted");
    } catch (IOException e) {
      throw new ApiFailure(502, "Slide upload failed");
    }
  }

  public void removeObjects(Caller c, String bucket, List<String> paths) {
    if (!paths.isEmpty())
      json(
          c.token(),
          "DELETE",
          "/storage/v1/object/" + bucket,
          Map.of(),
          Map.of("prefixes", paths),
          null);
  }

  public String publicSlideUrl(String path) {
    return publicUrl + "/storage/v1/object/public/lecture-slides/" + path;
  }

  private ApiFailure failure(int status) {
    return switch (status) {
      case 401 -> new ApiFailure(401, "Authentication expired or invalid");
      case 403 -> new ApiFailure(403, "Access denied");
      case 404, 406 -> ApiFailure.missing();
      case 409 -> new ApiFailure(409, "The resource changed or already exists");
      case 413 -> new ApiFailure(413, "File is too large");
      case 429 -> new ApiFailure(429, "Too many requests; try again shortly");
      default ->
          new ApiFailure(
              status < 500 ? 400 : 502,
              status < 500 ? "Request violates a data constraint" : "Supabase is unavailable");
    };
  }
}
