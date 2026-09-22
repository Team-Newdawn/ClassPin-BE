package com.ohpin.shared.infrastructure;

import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentGuard {
  public EnvironmentGuard(
      SupabaseProperties properties,
      Environment environment,
      @Value("${ohpin.allowed-origins}") String origins) {
    URI uri = URI.create(properties.url());
    if (environment.acceptsProfiles(Profiles.of("local"))) {
      if (!Set.of("localhost", "127.0.0.1", "host.docker.internal").contains(uri.getHost()))
        throw new IllegalArgumentException("Local profile must use the local Supabase CLI stack");
    } else if (!"https".equals(uri.getScheme())) {
      throw new IllegalArgumentException("Deployment Supabase URL must use HTTPS");
    }
    if (origins.isBlank() || origins.contains("*"))
      throw new IllegalArgumentException("Explicit frontend origins are required");
    if (properties.publishableKey().startsWith("sb_secret_"))
      throw new IllegalArgumentException("Use a Supabase publishable key");
  }
}
