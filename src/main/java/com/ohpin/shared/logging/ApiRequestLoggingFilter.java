package com.ohpin.shared.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ohpin.shared.security.Caller;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.slf4j.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

public final class ApiRequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);
  private static final int MAX_CAPTURE_BYTES = 64 * 1024;
  private static final int MAX_LOG_CHARS = 4 * 1024;

  private final ObjectMapper mapper;

  public ApiRequestLoggingFilter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/")
        || "OPTIONS".equalsIgnoreCase(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var wrapped = new ContentCachingRequestWrapper(request, MAX_CAPTURE_BYTES);
    long started = System.nanoTime();
    Throwable failure = null;
    try {
      chain.doFilter(wrapped, response);
    } catch (IOException | ServletException | RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      int status = failure == null ? response.getStatus() : 500;
      String message =
          "API method={} path={} status={} durationMs={} userId={} request={}";
      Object[] values = {
        request.getMethod(),
        safePath(request),
        status,
        (System.nanoTime() - started) / 1_000_000,
        userId(),
        requestBody(wrapped)
      };
      if (failure != null) {
        Object[] valuesWithFailure = Arrays.copyOf(values, values.length + 1);
        valuesWithFailure[values.length] = failure;
        log.error(message, valuesWithFailure);
      }
      else if (status >= 500) log.error(message, values);
      else if (status >= 400) log.warn(message, values);
      else log.info(message, values);
    }
  }

  private String safePath(HttpServletRequest request) {
    String path = request.getRequestURI().replace('\r', '_').replace('\n', '_');
    return path.length() <= 1_024 ? path : path.substring(0, 1_024) + "…";
  }

  private String userId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof Caller caller
        ? caller.id().toString()
        : "anonymous";
  }

  private String requestBody(ContentCachingRequestWrapper request) {
    byte[] content = request.getContentAsByteArray();
    if (content.length == 0) return "-";
    String contentType = request.getContentType();
    if (contentType == null
        || !(contentType.equalsIgnoreCase("application/json")
            || contentType.toLowerCase(Locale.ROOT).contains("+json"))) {
      return "<non-json bytes=" + request.getContentLengthLong() + ">";
    }
    if (content.length >= MAX_CAPTURE_BYTES) return "<json omitted: body exceeds 64 KiB>";
    try {
      JsonNode sanitized = sanitize(mapper.readTree(new String(content, StandardCharsets.UTF_8)));
      String value = mapper.writeValueAsString(sanitized);
      return value.length() <= MAX_LOG_CHARS
          ? value
          : value.substring(0, MAX_LOG_CHARS) + "…<truncated>";
    } catch (Exception ignored) {
      return "<invalid-json>";
    }
  }

  private JsonNode sanitize(JsonNode value) {
    if (value instanceof ObjectNode object) {
      var fields = object.properties().iterator();
      while (fields.hasNext()) {
        var field = fields.next();
        if (sensitive(field.getKey())) object.put(field.getKey(), "[REDACTED]");
        else sanitize(field.getValue());
      }
    } else if (value instanceof ArrayNode array) {
      array.forEach(this::sanitize);
    }
    return value;
  }

  private boolean sensitive(String key) {
    String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return normalized.contains("password")
        || normalized.contains("token")
        || normalized.contains("secret")
        || normalized.contains("apikey")
        || normalized.contains("authorization")
        || normalized.contains("credential");
  }
}
