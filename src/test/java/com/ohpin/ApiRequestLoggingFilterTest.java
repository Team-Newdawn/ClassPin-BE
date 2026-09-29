package com.ohpin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohpin.shared.logging.ApiRequestLoggingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class ApiRequestLoggingFilterTest {
  private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(new ObjectMapper());

  @Test
  void logsSuccessfulJsonApiRequestAtInfoAndRedactsSecrets(CapturedOutput output)
      throws Exception {
    var request = jsonRequest();
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, consumingChain(200));

    assertThat(output)
        .contains("INFO")
        .contains("API method=POST path=/api/instructor/folders status=200")
        .contains("\"name\":\"Course\"")
        .contains("\"accessToken\":\"[REDACTED]\"")
        .doesNotContain("do-not-log")
        .doesNotContain("Bearer credential");
  }

  @Test
  void logsClientErrorsAtWarn(CapturedOutput output) throws Exception {
    filter.doFilter(jsonRequest(), new MockHttpServletResponse(), consumingChain(400));

    assertThat(output)
        .contains("WARN")
        .contains("API method=POST path=/api/instructor/folders status=400");
  }

  @Test
  void logsServerErrorsAtError(CapturedOutput output) throws Exception {
    filter.doFilter(jsonRequest(), new MockHttpServletResponse(), consumingChain(500));

    assertThat(output)
        .contains("ERROR")
        .contains("API method=POST path=/api/instructor/folders status=500");
  }

  private MockHttpServletRequest jsonRequest() {
    var request = new MockHttpServletRequest("POST", "/api/instructor/folders");
    request.setContentType("application/json");
    request.addHeader("Authorization", "Bearer credential");
    request.setContent(
        "{\"name\":\"Course\",\"nested\":{\"accessToken\":\"do-not-log\"}}".getBytes());
    return request;
  }

  private FilterChain consumingChain(int status) {
    return (request, response) -> {
      request.getInputStream().readAllBytes();
      ((HttpServletResponse) response).setStatus(status);
    };
  }
}
