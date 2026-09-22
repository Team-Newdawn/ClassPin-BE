package com.ohpin.question.dto;

public record SubmitQuestionRequest(
    java.util.UUID id,
    java.util.UUID slideId,
    Double x,
    Double y,
    String category,
    String marker,
    String text) {}
