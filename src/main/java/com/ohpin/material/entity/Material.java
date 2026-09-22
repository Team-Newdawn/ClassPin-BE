package com.ohpin.material.entity;

import com.ohpin.lecture.entity.LectureSettings;
import com.ohpin.shared.domain.Rules;
import java.util.*;

public record Material(
    UUID id,
    UUID courseId,
    UUID materialId,
    UUID materialVersionId,
    UUID folderId,
    String title,
    String fileName,
    String sourcePath,
    String code,
    String status,
    Integer currentSlide,
    Boolean presentationInteractions,
    Boolean showQuestionPins,
    Boolean showPresentationQr,
    String presentationQrPosition,
    Map<String, Object> questionCategories,
    List<SlideDraft> slides) {
  public record SlideDraft(UUID id, Integer pageIndex, String imagePath, Integer sourcePageIndex) {}

  public Material {
    Rules.check(
        id != null && courseId != null && materialId != null && materialVersionId != null,
        "Material IDs are required");
    title = Rules.text(title, "Title", 1, 120);
    fileName = Rules.text(fileName, "File name", 1, 255);
    SourcePath.extension(fileName);
    Rules.check(code != null && code.matches("[A-Z0-9]{6,10}"), "Invalid join code");
    Rules.check(status != null && Set.of("live", "ended").contains(status), "Invalid status");
    Rules.check(
        presentationInteractions != null && showQuestionPins != null && showPresentationQr != null,
        "Presentation flags required");
    Rules.check(
        presentationQrPosition != null
            && LectureSettings.POSITIONS.contains(presentationQrPosition),
        "Invalid QR position");
    LectureSettings.categories(questionCategories);
    Rules.check(slides != null && !slides.isEmpty(), "Slides required");
    Rules.check(
        currentSlide != null && currentSlide >= 0 && currentSlide < slides.size(),
        "Invalid current slide");
    Set<UUID> ids = new HashSet<>();
    Set<Integer> sourcePages = new HashSet<>();
    for (int i = 0; i < slides.size(); i++) {
      var slide = slides.get(i);
      Rules.check(
          slide != null
              && slide.id() != null
              && ids.add(slide.id())
              && Objects.equals(slide.pageIndex(), i),
          "Invalid slide ID or order");
      Rules.check(
          (slide.imagePath() != null) != (slide.sourcePageIndex() != null),
          "Exactly one slide source is required");
      if (slide.sourcePageIndex() != null)
        Rules.check(
            slide.sourcePageIndex() >= 0
                && sourcePages.add(slide.sourcePageIndex())
                && SourcePath.extension(fileName).equals(".pdf"),
            "Invalid PDF page");
    }
    slides = List.copyOf(slides);
  }

  public void validateOwner(UUID owner) {
    SourcePath.validate(owner, sourcePath, fileName);
    slides.stream()
        .map(SlideDraft::imagePath)
        .filter(Objects::nonNull)
        .forEach(path -> SourcePath.owned(owner, path));
  }
}
