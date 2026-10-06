package com.ohpin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "ohpin.supabase.url=http://127.0.0.1:56321",
      "ohpin.supabase.publishable-key=test-publishable-key",
      "ohpin.allowed-origins=http://localhost:3000"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OpenApiDocumentationTest {
  @Autowired MockMvc mvc;

  @Test
  void exposesOpenApiDocumentWithoutAuthentication() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("OhPin API"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.schemas.UpdateLectureRequest.properties.current_page.type").value("integer"))
        .andExpect(jsonPath("$.components.schemas.UpdateLectureRequest.properties.allow_question_reactions.type").value("boolean"))
        .andExpect(jsonPath("$.components.schemas.UpdateLectureRequest.properties.allow_emoji_reactions.type").value("boolean"))
        .andExpect(jsonPath("$.components.schemas.UpdateLectureRequest.properties.join_code").doesNotExist());
  }

  @Test
  void exposesSwaggerUiWithoutAuthentication() throws Exception {
    mvc.perform(get("/swagger-ui/index.html"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"));
  }
}
