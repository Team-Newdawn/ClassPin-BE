package com.ohpin;

import static org.assertj.core.api.Assertions.*;

import com.ohpin.shared.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EnvironmentGuardTest {
  private MockEnvironment environment(String profile) {
    var e = new MockEnvironment();
    e.setActiveProfiles(profile);
    return e;
  }

  @Test
  void localProfileCannotUseRemoteDatabase() {
    assertThatThrownBy(
            () ->
                new EnvironmentGuard(
                    new SupabaseProperties("https://project.supabase.co", "key"),
                    environment("local"),
                    "http://localhost:3000"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void productionRequiresHttpsAndExactOrigins() {
    assertThatThrownBy(
            () ->
                new EnvironmentGuard(
                    new SupabaseProperties("http://localhost:56321", "key"),
                    environment("prod"),
                    "https://app.example"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new EnvironmentGuard(
                    new SupabaseProperties("https://project.supabase.co", "key"),
                    environment("prod"),
                    "*"))
        .isInstanceOf(IllegalArgumentException.class);
    new EnvironmentGuard(
        new SupabaseProperties("https://project.supabase.co", "key"),
        environment("prod"),
        "https://app.example");
  }
}
