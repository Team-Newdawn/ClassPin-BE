package com.ohpin.question.dto;

import com.ohpin.shared.domain.Rules;
import java.util.Set;

public record QuestionContent(String category, String marker, String text) {
  public QuestionContent {
    text = Rules.text(text, "Question", 1, 300);
    Rules.check(
        category != null && category.matches("[a-z0-9][a-z0-9-]{0,63}"),
        "Invalid question category");
    Rules.check(
        marker != null && Set.of("pin", "question", "smile", "idea").contains(marker),
        "Invalid marker");
  }
}
