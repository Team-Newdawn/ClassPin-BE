package com.ohpin;

import static org.assertj.core.api.Assertions.assertThat;

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
  private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter();

  @Test
  void logsSuccessfulApiRequestWithoutLoggingRequestBody(CapturedOutput output)
      throws Exception {
    var request = jsonRequest();
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, consumingChain(200));

    assertThat(output)
        .contains("INFO")
        .contains("event.name=\"http.server.request.completed\"")
        .contains("http.request.method=\"POST\"")
        .contains("http.route=\"/api/instructor/folders\"")
        .contains("http.response.status_code=\"200\"")
        .doesNotContain("Course")
        .doesNotContain("do-not-log")
        .doesNotContain("Bearer credential");
    assertThat(response.getHeader("X-Request-ID")).matches("[a-f0-9-]{36}");
  }

  @Test
  void logsClientErrorsAtInfo(CapturedOutput output) throws Exception {
    filter.doFilter(jsonRequest(), new MockHttpServletResponse(), consumingChain(400));

    assertThat(output)
        .contains("INFO")
        .contains("http.response.status_code=\"400\"");
  }

  @Test
  void logsServerErrorsAtError(CapturedOutput output) throws Exception {
    filter.doFilter(jsonRequest(), new MockHttpServletResponse(), consumingChain(500));

    assertThat(output)
        .contains("ERROR")
        .contains("http.response.status_code=\"500\"");
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
