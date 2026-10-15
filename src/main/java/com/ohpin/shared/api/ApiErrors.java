package com.ohpin.shared.api;

import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  @ExceptionHandler(ApiFailure.class)
  ResponseEntity<?> failure(ApiFailure e) {
    return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "Invalid request: check the supplied fields"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    if (e instanceof org.springframework.web.ErrorResponse error
        && error.getStatusCode().is4xxClientError())
      return ResponseEntity.status(error.getStatusCode())
          .body(Map.of("error", "Invalid HTTP request"));
    if (isClientDisconnect(e)) {
      log.atInfo()
          .addKeyValue("event.name", "http.server.client_disconnected")
          .addKeyValue("error.type", rootCause(e).getClass().getSimpleName())
          .log("Client disconnected before response completed");
    } else {
      log.atError()
          .addKeyValue("event.name", "http.server.unhandled_error")
          .addKeyValue("error.type", e.getClass().getSimpleName())
          .setCause(e)
          .log("Unhandled API error");
    }
    return ResponseEntity.internalServerError()
        .body(Map.of("error", "The request could not be completed"));
  }


  private boolean isClientDisconnect(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      String name = current.getClass().getSimpleName();
      String message = current.getMessage();
      if (current instanceof AsyncRequestNotUsableException
          || name.equals("ClientAbortException")
          || (current instanceof IOException
              && message != null
              && message.toLowerCase(java.util.Locale.ROOT).contains("broken pipe"))) return true;
    }
    return false;
  }

  private Throwable rootCause(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) current = current.getCause();
    return current;
  }
}
