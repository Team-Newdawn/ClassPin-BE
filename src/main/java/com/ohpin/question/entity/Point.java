package com.ohpin.question.entity;

import com.ohpin.shared.domain.Rules;

public record Point(Double x, Double y) {
  public Point {
    Rules.check(valid(x) && valid(y), "Point coordinates must be finite and between 0 and 1");
  }

  private static boolean valid(Double n) {
    return n != null && Double.isFinite(n) && n >= 0 && n <= 1;
  }
}
