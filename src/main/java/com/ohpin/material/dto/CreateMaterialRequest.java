package com.ohpin.material.dto;

import com.ohpin.material.entity.Material;
import java.util.*;

public record CreateMaterialRequest(
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
    List<Material.SlideDraft> slides) {
  public Material toEntity() {
    return new Material(
        id,
        courseId,
        materialId,
        materialVersionId,
        folderId,
        title,
        fileName,
        sourcePath,
        code,
        status,
        currentSlide,
        presentationInteractions,
        showQuestionPins,
        showPresentationQr,
        presentationQrPosition,
        questionCategories,
        slides);
  }
}
