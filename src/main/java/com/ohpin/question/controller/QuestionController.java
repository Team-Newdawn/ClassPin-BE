package com.ohpin.question.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.question.dto.AnswerRequest;
import com.ohpin.question.dto.QuestionContent;
import com.ohpin.question.dto.QuestionReactionRequest;
import com.ohpin.question.dto.SubmitQuestionRequest;
import com.ohpin.question.entity.Point;
import com.ohpin.question.service.QuestionService;
import com.ohpin.shared.security.Caller;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class QuestionController {
  private final QuestionService service;

  public QuestionController(QuestionService service) {
    this.service = service;
  }

  @GetMapping({
    "/api/instructor/lectures/{id}/questions",
    "/api/participant/lectures/{id}/questions"
  })
  JsonNode list(@AuthenticationPrincipal Caller c, @PathVariable UUID id) {
    return service.list(c, id);
  }

  @PostMapping("/api/participant/lectures/{lectureId}/questions")
  void submit(
      @AuthenticationPrincipal Caller c,
      @PathVariable UUID lectureId,
      @RequestBody SubmitQuestionRequest b) {
    service.submit(
        c,
        b.id(),
        lectureId,
        b.slideId(),
        new Point(b.x(), b.y()),
        new QuestionContent(b.category(), b.marker(), b.text()));
  }

  @PatchMapping("/api/participant/questions/{id}")
  void edit(
      @AuthenticationPrincipal Caller c, @PathVariable UUID id, @RequestBody QuestionContent b) {
    service.edit(c, id, b);
  }

  @PutMapping("/api/participant/questions/{id}/reaction")
  void react(
      @AuthenticationPrincipal Caller c,
      @PathVariable UUID id,
      @RequestBody QuestionReactionRequest b) {
    service.react(c, id, b.reacted());
  }

  @DeleteMapping("/api/participant/questions/{id}")
  void delete(@AuthenticationPrincipal Caller c, @PathVariable UUID id) {
    service.delete(c, id);
  }

  @PostMapping("/api/instructor/questions/{id}/answers")
  void answer(
      @AuthenticationPrincipal Caller c, @PathVariable UUID id, @RequestBody AnswerRequest b) {
    service.answer(c, id, b.body());
  }

  @PutMapping("/api/instructor/questions/{id}/resolved")
  void resolve(@AuthenticationPrincipal Caller c, @PathVariable UUID id) {
    service.resolve(c, id);
  }
}
