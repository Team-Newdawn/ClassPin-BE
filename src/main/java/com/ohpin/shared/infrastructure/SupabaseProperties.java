package com.ohpin.shared.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ohpin.supabase")
public record SupabaseProperties(@NotBlank String url, @NotBlank String publishableKey) {}
