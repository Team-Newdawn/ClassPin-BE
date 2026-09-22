package com.ohpin.shared.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.shared.security.Caller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProfileController {
  @GetMapping("/api/me")
  JsonNode profile(@AuthenticationPrincipal Caller caller) {
    return caller.profile();
  }
}
