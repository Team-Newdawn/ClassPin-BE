package com.ohpin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohpin.lecture.repository.SupabaseLectureRepository;
import com.ohpin.material.repository.SupabaseMaterialRepository;
import com.ohpin.question.repository.SupabaseQuestionRepository;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;
import java.util.*;
import org.junit.jupiter.api.Test;

class RepositoryBoundaryTest {
  private final SupabaseGateway db = mock(SupabaseGateway.class);
  private final Caller participant =
      new Caller(UUID.randomUUID(), "participant-token", false, null);

  @Test
  void participantSlidesCannotSelectInstructorNotes() {
    new SupabaseMaterialRepository(db).slides(participant, UUID.randomUUID());
    verify(db)
        .get(
            eq(participant),
            eq("slides"),
            argThat(q -> !q.get("select").contains("note") && !q.get("select").contains("*")));
  }

  @Test
  void publicLectureGraphHasNoNotesOrQuestionsWithAuthorIdentity() {
    when(db.get(any(), anyString(), anyMap())).thenReturn(new ObjectMapper().createArrayNode());
    new SupabaseLectureRepository(db).liveGraph(participant, "join_code", "ABC123");
    verify(db)
        .get(
            eq(participant),
            eq("lectures"),
            argThat(
                q ->
                    !q.get("select").contains("note")
                        && !q.get("select").contains("author")
                        && q.get("status").equals("eq.live")));
  }

  @Test
  void publicQuestionsUseExistingSanitizedRpc() {
    UUID id = UUID.randomUUID();
    new SupabaseQuestionRepository(db).list(participant, id);
    verify(db).rpc(participant, "find_lecture_questions", Map.of("target_lecture_id", id));
    verify(db, never()).get(any(), eq("questions"), anyMap());
  }

  @Test
  void missingWriteRowsAreNotReportedAsSuccess() {
    assertThatThrownBy(() -> SupabaseGateway.required(new ObjectMapper().createArrayNode()))
        .hasMessageContaining("not found");
  }
}
