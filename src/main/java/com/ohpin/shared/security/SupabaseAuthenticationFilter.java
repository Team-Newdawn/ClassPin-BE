package com.ohpin.shared.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohpin.shared.api.ApiFailure;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SupabaseAuthenticationFilter extends OncePerRequestFilter {
  private static final long CACHE_TTL_MILLIS = 30_000;
  private static final int MAX_CACHE_ENTRIES = 4_096;
  private static final ObjectMapper JWT_JSON = new ObjectMapper();

  private final SupabaseGateway gateway;
  private final Map<String, CachedIdentity> identities = new LinkedHashMap<>(128, 0.75f, true);

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
      String tokenHash = tokenHash(token);
      long now = System.currentTimeMillis();
      CachedIdentity identity = cached(tokenHash, now);
      if (identity == null) {
        var user = gateway.json(token, "GET", "/auth/v1/user", Map.of(), null, null);
        UUID id = UUID.fromString(user.path("id").asText());
        boolean anonymous = user.path("is_anonymous").asBoolean(true);
        JsonNode profile = null;
        if (!anonymous) {
          var preliminary = new Caller(id, token, false, null);
          profile =
              SupabaseGateway.first(
                  gateway.get(
                      preliminary,
                      "profiles",
                      Map.of("select", "id,role,email,display_name,avatar_url", "id", "eq." + id)));
        }
        long expiresAt = cacheExpiry(token, now);
        identity = new CachedIdentity(id, anonymous, profile, expiresAt);
        cache(tokenHash, identity, now);
      }
      boolean instructor =
          !identity.anonymous()
              && identity.profile() != null
              && identity.profile().path("role").asText().equals("admin");
      var caller = new Caller(identity.id(), token, instructor, identity.profile());
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

  private String tokenHash(String token) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private CachedIdentity cached(String tokenHash, long now) {
    synchronized (identities) {
      CachedIdentity value = identities.get(tokenHash);
      if (value != null && value.expiresAtMillis() <= now) {
        identities.remove(tokenHash);
        return null;
      }
      return value;
    }
  }

  private void cache(String tokenHash, CachedIdentity value, long now) {
    if (value.expiresAtMillis() <= now) return;
    synchronized (identities) {
      identities.put(tokenHash, value);
      while (identities.size() > MAX_CACHE_ENTRIES) {
        identities.remove(identities.keySet().iterator().next());
      }
    }
  }

  private long cacheExpiry(String token, long now) {
    try {
      String[] parts = token.split("\\.", -1);
      if (parts.length != 3) return 0;
      JsonNode claims = JWT_JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
      long expirationSeconds = claims.path("exp").asLong(0);
      if (expirationSeconds <= 0 || expirationSeconds > Long.MAX_VALUE / 1_000) return 0;
      return Math.min(now + CACHE_TTL_MILLIS, expirationSeconds * 1_000);
    } catch (IOException | IllegalArgumentException e) {
      return 0;
    }
  }

  private record CachedIdentity(UUID id, boolean anonymous, JsonNode profile, long expiresAtMillis) {}
}
