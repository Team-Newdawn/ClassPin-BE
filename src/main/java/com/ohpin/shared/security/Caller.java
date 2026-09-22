package com.ohpin.shared.security;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record Caller(UUID id, String token, boolean instructor, JsonNode profile) {
  @Override
  public String toString() {
    return "Caller[" + id + "]";
  }
}
