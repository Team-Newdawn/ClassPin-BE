package com.ohpin;

import static org.assertj.core.api.Assertions.*;

import com.ohpin.experience.entity.Experience;
import com.ohpin.lecture.entity.LectureSettings;
import com.ohpin.question.dto.QuestionContent;
import java.util.*;
import org.junit.jupiter.api.Test;

class LectureRulesTest {
  @Test
  void settingsCannotTransferOwnershipOrInjectArbitraryColumns() {
    assertThatThrownBy(() -> LectureSettings.validate(Map.of("owner_id", UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LectureSettings.validate(Map.of("current_page", -1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LectureSettings.validate(Map.of("status", "archived")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LectureSettings.validate(Map.of("show_question_pins", "true")))
        .isInstanceOf(IllegalArgumentException.class);
    LectureSettings.validate(
        Map.of(
            "current_page",
            1,
            "status",
            "live",
            "show_question_pins",
            true,
            "presentation_autoplay",
            false));
  }

  @Test
  void atLeastOneCategoryMustStayEnabled() {
    assertThatThrownBy(
            () ->
                LectureSettings.categories(
                    Map.of("concept", Map.of("label", "", "enabled", false, "archived", false))))
        .isInstanceOf(IllegalArgumentException.class);
    LectureSettings.categories(
        Map.of("concept", Map.of("label", "", "enabled", true, "archived", false)));
  }

  @Test
  void questionAndExperienceBoundsMatchDatabase() {
    assertThat(new QuestionContent("concept", "pin", " question ").text()).isEqualTo("question");
    assertThatThrownBy(() -> new QuestionContent("concept", "other", "q"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QuestionContent("concept", "pin", "x".repeat(301)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new Experience("abc123", " good ", " more ").code()).isEqualTo("ABC123");
    assertThatThrownBy(() -> new Experience("ABC123", " ", "more"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
