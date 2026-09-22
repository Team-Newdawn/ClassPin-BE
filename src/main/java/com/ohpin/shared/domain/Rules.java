package com.ohpin.shared.domain;

public final class Rules {
  private Rules() {}

  public static String text(String value, String field, int min, int max) {
    if (value == null) throw new IllegalArgumentException(field + " is required");
    String result = value.strip();
    int length = result.codePointCount(0, result.length());
    if (length < min || length > max)
      throw new IllegalArgumentException(
          field + " must contain " + min + " to " + max + " characters");
    return result;
  }

  public static void check(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}
