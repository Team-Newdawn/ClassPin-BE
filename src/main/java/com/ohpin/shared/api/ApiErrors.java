package com.ohpin.shared.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
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
    org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("Unhandled API error", e);
    return ResponseEntity.internalServerError()
        .body(Map.of("error", "The request could not be completed"));
  }
}
