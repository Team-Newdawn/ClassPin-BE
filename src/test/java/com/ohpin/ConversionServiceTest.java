package com.ohpin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohpin.material.dto.ConversionRequest;
import com.ohpin.material.service.*;
import com.ohpin.shared.api.ApiFailure;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.Test;

class ConversionServiceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final SupabaseGateway storage = mock(SupabaseGateway.class);
  private final DocumentConverter converter = mock(DocumentConverter.class);
  private final Caller owner = new Caller(UUID.randomUUID(), "token", true, null);
  private final ConversionRequest request =
      new ConversionRequest(owner.id() + "/upload/source.pdf", "source.pdf");

  private ConversionService ready() {
    when(storage.json(anyString(), eq("GET"), anyString(), anyMap(), isNull(), isNull()))
        .thenReturn(json.valueToTree(Map.of("size", 100)));
    when(converter.toPdf(any(), any())).thenAnswer(call -> call.getArgument(0));
    when(converter.pageCount(any(), any())).thenReturn(1);
    return new ConversionService(storage, converter, json, 1);
  }

  @Test
  void abandonedResponseDoesNotReserveAConversionSlot() throws Exception {
    var service = ready();
    service.convert(owner, request);
    var output = new ByteArrayOutputStream();
    service.convert(owner, request).writeTo(output);
    assertThat(output.toString()).contains("\"type\":\"done\"").doesNotContain("busy");
  }

  @Test
  void failedConversionReleasesSlotAndSendsErrorEvent() throws Exception {
    var service = ready();
    when(converter.pageCount(any(), any()))
        .thenThrow(new ApiFailure(422, "Invalid PDF"))
        .thenReturn(1);
    var failed = new ByteArrayOutputStream();
    service.convert(owner, request).writeTo(failed);
    assertThat(failed.toString()).contains("\"type\":\"error\"");
    var retry = new ByteArrayOutputStream();
    service.convert(owner, request).writeTo(retry);
    assertThat(retry.toString()).contains("\"type\":\"done\"");
  }

  @Test
  void oversizedSourceIsRejectedBeforeStreaming() {
    var service = ready();
    when(storage.json(anyString(), eq("GET"), anyString(), anyMap(), isNull(), isNull()))
        .thenReturn(json.valueToTree(Map.of("size", 1024L * 1024 * 1024 + 1)));
    assertThatThrownBy(() -> service.convert(owner, request))
        .isInstanceOf(ApiFailure.class)
        .hasMessageContaining("1 GiB");
    verifyNoInteractions(converter);
  }
}
