package com.ohpin.shared.logging;

import com.ohpin.shared.security.Caller;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.*;
import org.slf4j.event.Level;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ApiRequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);
  private static final String REQUEST_ID = "request.id";
  private static final long SLOW_REQUEST_MS = 1_000;
  private static final Pattern SAFE_REQUEST_ID =
      Pattern.compile("(?:[a-fA-F0-9]{32}|[a-fA-F0-9]{8}-(?:[a-fA-F0-9]{4}-){3}[a-fA-F0-9]{12})");
  private static final Pattern UUID_PATH_SEGMENT =
      Pattern.compile("/[a-fA-F0-9]{8}-(?:[a-fA-F0-9]{4}-){3}[a-fA-F0-9]{12}(?=/|$)");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/")
        || "OPTIONS".equalsIgnoreCase(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = requestId(request.getHeader("X-Request-ID"));
    String previousRequestId = MDC.get(REQUEST_ID);
    MDC.put(REQUEST_ID, requestId);
    response.setHeader("X-Request-ID", requestId);
    long started = System.nanoTime();
    Throwable failure = null;
    try {
      chain.doFilter(request, response);
    } catch (IOException | ServletException | RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      long durationMs = (System.nanoTime() - started) / 1_000_000;
      boolean clientDisconnected = isClientDisconnect(failure);
      int status = failure == null || clientDisconnected ? response.getStatus() : 500;
      Level level = level(status, durationMs, failure, clientDisconnected);
      try {
        var event = log.atLevel(level)
            .addKeyValue("event.name", clientDisconnected ? "http.server.client_disconnected" : "http.server.request.completed")
            .addKeyValue("http.request.method", request.getMethod())
            .addKeyValue("http.route", route(request.getRequestURI()))
            .addKeyValue("http.response.status_code", status)
            .addKeyValue("event.duration_ms", durationMs)
            .addKeyValue("user.type", userType());
        if (failure != null && !clientDisconnected) {
          event.addKeyValue("error.type", failure.getClass().getSimpleName()).setCause(failure);
        }
        event.log(clientDisconnected ? "Client disconnected before response completed" : "HTTP request completed");
      } finally {
        if (previousRequestId == null) MDC.remove(REQUEST_ID);
        else MDC.put(REQUEST_ID, previousRequestId);
      }
    }
  }

  private Level level(int status, long durationMs, Throwable failure, boolean clientDisconnected) {
    if (clientDisconnected) return Level.INFO;
    if (failure != null || status >= 500) return Level.ERROR;
    if (status == 429 || durationMs >= SLOW_REQUEST_MS) return Level.WARN;
    return Level.INFO;
  }

  private String requestId(String provided) {
    return provided != null && SAFE_REQUEST_ID.matcher(provided).matches()
        ? provided
        : UUID.randomUUID().toString();
  }

  private String route(String path) {
    String normalized = UUID_PATH_SEGMENT.matcher(path).replaceAll("/{id}");
    return normalized.replaceFirst("^(/api/participant/join/)[A-Za-z0-9]{6,10}$", "$1{code}");
  }

  private String userType() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Caller caller))
      return "anonymous";
    return caller.instructor() ? "instructor" : "participant";
  }

  private boolean isClientDisconnect(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      String name = current.getClass().getSimpleName();
      String message = current.getMessage();
      if (name.equals("ClientAbortException")
          || name.equals("AsyncRequestNotUsableException")
          || (current instanceof IOException
              && message != null
              && message.toLowerCase(java.util.Locale.ROOT).contains("broken pipe"))) return true;
    }
    return false;
  }
}
