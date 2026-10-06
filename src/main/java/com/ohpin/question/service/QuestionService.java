package com.ohpin.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.question.dto.QuestionContent;
import com.ohpin.question.entity.Point;
import com.ohpin.question.repository.QuestionRepository;
import com.ohpin.shared.domain.Rules;
import com.ohpin.shared.security.Caller;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {
  private final QuestionRepository repository;

  public QuestionService(QuestionRepository repository) {
    this.repository = repository;
  }

  public JsonNode list(Caller c, UUID lectureId) {
    return repository.list(c, lectureId);
  }

  public void submit(
      Caller c, UUID id, UUID lectureId, UUID slideId, Point point, QuestionContent content) {
    Rules.check(id != null && slideId != null, "Question and slide IDs are required");
    repository.submit(c, id, lectureId, slideId, point, content);
  }

  public void edit(Caller c, UUID id, QuestionContent content) {
    repository.edit(c, id, content);
  }

  public void react(Caller c, UUID id, Boolean reacted) {
    Rules.check(reacted != null, "Reaction is required");
    repository.react(c, id, reacted);
  }

  public void delete(Caller c, UUID id) {
    repository.delete(c, id);
  }

  public void answer(Caller c, UUID id, String body) {
    repository.answer(c, id, Rules.text(body, "Answer", 1, 2000));
  }

  public void resolve(Caller c, UUID id) {
    repository.resolve(c, id);
  }
}
