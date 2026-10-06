package com.ohpin.question.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.question.dto.QuestionContent;
import com.ohpin.question.entity.Point;
import com.ohpin.shared.security.Caller;
import java.util.UUID;

public interface QuestionRepository {
  JsonNode list(Caller c, UUID lectureId);

  void submit(
      Caller c, UUID id, UUID lectureId, UUID slideId, Point point, QuestionContent content);

  void edit(Caller c, UUID id, QuestionContent content);

  void react(Caller c, UUID id, boolean reacted);

  void delete(Caller c, UUID id);

  void answer(Caller c, UUID id, String body);

  void resolve(Caller c, UUID id);
}
