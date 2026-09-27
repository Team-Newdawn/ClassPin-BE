package com.ohpin;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohpin.folder.controller.FolderController;
import com.ohpin.folder.service.FolderService;
import com.ohpin.shared.api.ApiFailure;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.SecurityConfig;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = FolderController.class,
    properties = {
      "ohpin.allowed-origins=http://localhost:3000,http://localhost:3001,http://localhost:3002"
    })
@Import(SecurityConfig.class)
class SecurityBoundaryTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean FolderService folders;
  @MockitoBean SupabaseGateway gateway;

  private void authenticate(boolean instructor) {
    UUID id = UUID.randomUUID();
    when(gateway.json(
            eq("valid-token"), eq("GET"), eq("/auth/v1/user"), anyMap(), isNull(), isNull()))
        .thenReturn(json.valueToTree(Map.of("id", id.toString(), "is_anonymous", !instructor)));
    when(gateway.get(any(), eq("profiles"), anyMap()))
        .thenReturn(
            json.valueToTree(
                List.of(
                    Map.of("id", id.toString(), "role", instructor ? "admin" : "participant"))));
  }

  @Test
  void missingTokenFailsClosed() throws Exception {
    mvc.perform(get("/api/instructor/folders")).andExpect(status().isUnauthorized());
    verifyNoInteractions(folders, gateway);
  }

  @Test
  void invalidTokenCannotReachService() throws Exception {
    when(gateway.json(anyString(), anyString(), anyString(), anyMap(), isNull(), isNull()))
        .thenThrow(new ApiFailure(401, "Invalid token"));
    mvc.perform(get("/api/instructor/folders").header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(folders);
  }

  @Test
  void anonymousUserCannotUseInstructorApi() throws Exception {
    authenticate(false);
    mvc.perform(get("/api/instructor/folders").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(folders);
  }

  @Test
  void instructorCanListFolders() throws Exception {
    authenticate(true);
    when(folders.list(any())).thenReturn(json.createArrayNode());
    mvc.perform(get("/api/instructor/folders").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    verify(folders).list(argThat(c -> c.instructor() && c.token().equals("valid-token")));
  }

  @Test
  void unknownWriteFieldsAreRejected() throws Exception {
    authenticate(true);
    mvc.perform(
            post("/api/instructor/folders")
                .header("Authorization", "Bearer valid-token")
                .contentType("application/json")
                .content(
                    "{\"name\":\"Course\",\"colorIndex\":0,\"purpose\":\"education\",\"purposeLabel\":null,\"owner_id\":\"someone-else\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(folders);
  }

  @Test
  void untrustedCorsOriginIsRejected() throws Exception {
    mvc.perform(
            options("/api/instructor/folders")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }

  @Test
  void configuredLocalCorsOriginsAreAllowed() throws Exception {
    for (String origin :
        List.of("http://localhost:3000", "http://localhost:3001", "http://localhost:3002")) {
      mvc.perform(
              options("/api/instructor/folders")
                  .header("Origin", origin)
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }
  }
}
