package com.ohpin.shared.api;

public class ApiFailure extends RuntimeException {
  private final int status;

  public ApiFailure(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }

  public static ApiFailure missing() {
    return new ApiFailure(404, "Resource was not found or access was denied");
  }
}
