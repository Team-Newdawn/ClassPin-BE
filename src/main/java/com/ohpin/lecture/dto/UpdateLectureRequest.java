package com.ohpin.lecture.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.LinkedHashMap;
import java.util.Map;

public record UpdateLectureRequest(
    @JsonProperty("current_page") @JsonSetter(nulls = Nulls.FAIL) Integer currentPage,
    @JsonSetter(nulls = Nulls.FAIL) String status,
    @JsonProperty("presentation_interactions") @JsonSetter(nulls = Nulls.FAIL)
        Boolean presentationInteractions,
    @JsonProperty("presentation_autoplay") @JsonSetter(nulls = Nulls.FAIL)
        Boolean presentationAutoplay,
    @JsonProperty("show_question_pins") @JsonSetter(nulls = Nulls.FAIL) Boolean showQuestionPins,
    @JsonProperty("show_presentation_qr") @JsonSetter(nulls = Nulls.FAIL)
        Boolean showPresentationQr,
    @JsonProperty("presentation_qr_position") @JsonSetter(nulls = Nulls.FAIL)
        String presentationQrPosition,
    @JsonProperty("question_categories") @JsonSetter(nulls = Nulls.FAIL)
        Map<String, Object> questionCategories,
    @JsonProperty("allow_question_reactions") @JsonSetter(nulls = Nulls.FAIL)
        Boolean allowQuestionReactions,
    @JsonProperty("allow_emoji_reactions") @JsonSetter(nulls = Nulls.FAIL)
        Boolean allowEmojiReactions) {

  public Map<String, Object> toFields() {
    var fields = new LinkedHashMap<String, Object>();
    if (currentPage != null) fields.put("current_page", currentPage);
    if (status != null) fields.put("status", status);
    if (presentationInteractions != null)
      fields.put("presentation_interactions", presentationInteractions);
    if (presentationAutoplay != null) fields.put("presentation_autoplay", presentationAutoplay);
    if (showQuestionPins != null) fields.put("show_question_pins", showQuestionPins);
    if (showPresentationQr != null) fields.put("show_presentation_qr", showPresentationQr);
    if (presentationQrPosition != null)
      fields.put("presentation_qr_position", presentationQrPosition);
    if (questionCategories != null) fields.put("question_categories", questionCategories);
    if (allowQuestionReactions != null)
      fields.put("allow_question_reactions", allowQuestionReactions);
    if (allowEmojiReactions != null)
      fields.put("allow_emoji_reactions", allowEmojiReactions);
    return fields;
  }
}
