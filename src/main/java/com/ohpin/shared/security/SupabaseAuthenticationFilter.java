package com.ohpin.shared.security;

import com.ohpin.shared.api.ApiFailure;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SupabaseAuthenticationFilter extends OncePerRequestFilter {
  private final SupabaseGateway gateway;

  public SupabaseAuthenticationFilter(SupabaseGateway gateway) {
    this.gateway = gateway;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    return r.getRequestURI().startsWith("/api/demo/")
        || !r.getRequestURI().startsWith("/api/")
        || r.getMethod().equals("OPTIONS");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String authorization = req.getHeader("Authorization");
    if (authorization == null || !authorization.matches("(?i)Bearer [^\\s]+")) {
      reject(res, 401);
      return;
    }
    try {
      String token = authorization.substring(7);
      var user = gateway.json(token, "GET", "/auth/v1/user", Map.of(), null, null);
      UUID id = UUID.fromString(user.path("id").asText());
      var preliminary = new Caller(id, token, false, null);
      var profile =
          SupabaseGateway.first(
              gateway.get(
                  preliminary,
                  "profiles",
                  Map.of("select", "id,role,email,display_name,avatar_url", "id", "eq." + id)));
      boolean instructor =
          !user.path("is_anonymous").asBoolean(true)
              && profile != null
              && profile.path("role").asText().equals("admin");
      var caller = new Caller(id, token, instructor, profile);
      var auth =
          new UsernamePasswordAuthenticationToken(
              caller,
              null,
              List.of(
                  new SimpleGrantedAuthority(instructor ? "ROLE_INSTRUCTOR" : "ROLE_PARTICIPANT")));
      SecurityContextHolder.getContext().setAuthentication(auth);
    } catch (ApiFailure e) {
      reject(res, e.status());
      return;
    } catch (IllegalArgumentException e) {
      reject(res, 401);
      return;
    }
    chain.doFilter(req, res);
  }

  private void reject(HttpServletResponse response, int status) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"Authentication could not be verified\"}");
  }
}
