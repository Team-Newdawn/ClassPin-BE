package com.ohpin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ohpin.shared.api.ProfileController;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ProfileController.class,
    properties = "ohpin.allowed-origins=https://app.example")
@Import(SecurityConfig.class)
@ActiveProfiles("prod")
class ProductionDemoBoundaryTest {
  @Autowired MockMvc mvc;
  @MockitoBean SupabaseGateway gateway;

  @Test
  void deploymentDoesNotExposeAnonymousDemoConversion() throws Exception {
    mvc.perform(post("/api/demo/convert").contentType("application/pdf").content("fake pdf"))
        .andExpect(status().is4xxClientError());
  }
}
