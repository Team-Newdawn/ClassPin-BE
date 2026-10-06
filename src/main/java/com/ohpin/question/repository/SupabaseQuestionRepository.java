package com.ohpin.question.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.question.dto.QuestionContent;
import com.ohpin.question.entity.Point;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class SupabaseQuestionRepository implements QuestionRepository {
  private final SupabaseGateway db;

  public SupabaseQuestionRepository(SupabaseGateway db) {
    this.db = db;
  }

  public JsonNode list(Caller c, UUID lectureId) {
    if (!c.instructor())
      return db.rpc(c, "find_lecture_questions", Map.of("target_lecture_id", lectureId));
    return db.get(
        c,
        "questions",
        Map.of(
            "select",
            "id,lecture_id,slide_id,category,marker,raw_text,status,reaction_count,created_at,region_anchors(kind,coords),answers(body,created_at)",
            "lecture_id",
            "eq." + lectureId,
            "order",
            "created_at.desc"));
  }

  public void submit(
      Caller c, UUID id, UUID lectureId, UUID slideId, Point point, QuestionContent content) {
    db.rpc(
        c,
        "ohpin_submit_point_question",
        Map.of(
            "target_id",
            id,
            "target_lecture_id",
            lectureId,
            "target_slide_id",
            slideId,
            "target_x",
            point.x(),
            "target_y",
            point.y(),
            "target_category",
            content.category(),
            "target_marker",
            content.marker(),
            "target_text",
            content.text()));
  }

  public void edit(Caller c, UUID id, QuestionContent q) {
    db.patch(
        c,
        "questions",
        Map.of(
            "id",
            "eq." + id,
            "author_id",
            "eq." + c.id(),
            "status",
            "eq.unanswered",
            "select",
            "id"),
        Map.of(
            "category",
            q.category(),
            "marker",
            q.marker(),
            "raw_text",
            q.text(),
            "updated_at",
            Instant.now().toString()));
  }

  public void react(Caller c, UUID id, boolean reacted) {
    db.rpc(c, "set_question_reaction", Map.of("target_question_id", id, "target_reacted", reacted));
  }

  public void delete(Caller c, UUID id) {
    db.rpc(c, "ohpin_delete_participant_question", Map.of("target_question_id", id));
  }

  public void answer(Caller c, UUID id, String body) {
    db.insertOnly(
        c,
        "answers",
        Map.of("question_id", id, "author_id", c.id(), "body", body, "visibility", "participants"));
  }

  public void resolve(Caller c, UUID id) {
    db.patch(
        c,
        "questions",
        Map.of("id", "eq." + id, "select", "id"),
        Map.of("status", "resolved", "updated_at", Instant.now().toString()));
  }
}
