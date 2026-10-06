package com.ohpin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohpin.lecture.repository.LectureRepository;
import com.ohpin.lecture.service.LectureService;
import com.ohpin.shared.security.Caller;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LectureServiceTest {
  private final LectureRepository repository = mock(LectureRepository.class);
  private final LectureService service = new LectureService(repository);
  private final Caller participant =
      new Caller(UUID.randomUUID(), "participant-token", false, null);

  @Test
  void participantResponsesSeparateBeforeEndedMissingAndLive() {
    UUID id = UUID.randomUUID();
    when(repository.audienceStatus(participant, id, null))
        .thenReturn("before", "pending", "ended", "archived", null, "live");

    assertThat(service.live(participant, id).status()).isEqualTo(204);
    assertThat(service.live(participant, id).status()).isEqualTo(204);
    assertThat(service.live(participant, id).status()).isEqualTo(410);
    assertThat(service.live(participant, id).status()).isEqualTo(404);
    assertThat(service.live(participant, id).status()).isEqualTo(404);

    var graph = new ObjectMapper().createObjectNode().put("status", "live");
    when(repository.liveGraph(participant, "id", id.toString())).thenReturn(graph);
    var live = service.live(participant, id);
    assertThat(live.status()).isEqualTo(200);
    assertThat(live.body()).isSameAs(graph);
  }
}
