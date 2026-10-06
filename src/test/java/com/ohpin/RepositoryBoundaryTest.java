package com.ohpin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohpin.folder.entity.Folder;
import com.ohpin.folder.repository.SupabaseFolderRepository;
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
  void folderProjectionAndInsertIncludePurposeFields() {
    var rows = new ObjectMapper().createArrayNode();
    rows.addObject().put("id", UUID.randomUUID().toString());
    when(db.insert(eq(participant), eq("session_folders"), any())).thenReturn(rows);

    var repository = new SupabaseFolderRepository(db);
    repository.list(participant);
    repository.create(participant, new Folder("Course", 2, "other", "Regular class"));

    verify(db)
        .get(
            eq(participant),
            eq("session_folders"),
            argThat(
                query ->
                    Arrays.asList(query.get("select").split(",")).contains("purpose")
                        && Arrays.asList(query.get("select").split(","))
                            .contains("purpose_label")));
    verify(db)
        .insert(
            eq(participant),
            eq("session_folders"),
            argThat(
                body ->
                    body instanceof Map<?, ?> row
                        && "other".equals(row.get("purpose"))
                        && "Regular class".equals(row.get("purpose_label"))));
  }

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
                        && q.get("select").contains("presentation_autoplay")
                        && q.get("select").contains("started_at")
                        && !q.get("select").contains("ended_at")
                        && q.get("status").equals("eq.live")));
  }

  @Test
  void lectureUpdatesAndParticipantDeletesUseGuardedRpcs() {
    UUID lecture = UUID.randomUUID();
    UUID question = UUID.randomUUID();
    var repository = new SupabaseLectureRepository(db);
    repository.update(participant, lecture, Map.of("status", "pending"));
    new SupabaseQuestionRepository(db).delete(participant, question);

    verify(db)
        .rpc(
            participant,
            "ohpin_update_lecture_settings",
            Map.of("target_lecture_id", lecture, "settings", Map.of("status", "pending")));
    verify(db)
        .rpc(
            participant,
            "ohpin_delete_participant_question",
            Map.of("target_question_id", question));
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
