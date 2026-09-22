package com.ohpin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohpin.material.service.StorageCleanupService;
import com.ohpin.shared.api.ApiFailure;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;
import java.util.*;
import org.junit.jupiter.api.Test;

class StorageCleanupTest {
  private final SupabaseGateway db = mock(SupabaseGateway.class);
  private final Caller owner = new Caller(UUID.randomUUID(), "token", true, null);
  private final ObjectMapper json = new ObjectMapper();
  private final String path = owner.id() + "/upload/source.pdf";

  private void pending() {
    when(db.get(eq(owner), eq("storage_cleanup_jobs"), anyMap()))
        .thenReturn(
            json.valueToTree(
                List.of(
                    Map.of(
                        "id",
                        UUID.randomUUID().toString(),
                        "bucket",
                        "course-materials",
                        "object_path",
                        path))));
  }

  @Test
  void referencedSourceIsNeverRemoved() {
    pending();
    when(db.get(eq(owner), eq("material_versions"), anyMap()))
        .thenReturn(json.valueToTree(List.of(Map.of("id", "still-used"))));
    assertThat(new StorageCleanupService(db).retry(owner)).isFalse();
    verify(db, never()).removeObjects(any(), anyString(), anyList());
    verify(db, never()).delete(any(), anyString(), anyMap());
  }

  @Test
  void storageFailureRetainsJobForRetry() {
    pending();
    when(db.get(eq(owner), eq("material_versions"), anyMap())).thenReturn(json.createArrayNode());
    doThrow(new ApiFailure(502, "unavailable"))
        .when(db)
        .removeObjects(any(), anyString(), anyList());
    assertThat(new StorageCleanupService(db).retry(owner)).isFalse();
    verify(db, never()).delete(any(), anyString(), anyMap());
  }

  @Test
  void successfulCleanupRemovesJobOnlyAfterObject() {
    pending();
    when(db.get(eq(owner), eq("material_versions"), anyMap())).thenReturn(json.createArrayNode());
    assertThat(new StorageCleanupService(db).retry(owner)).isTrue();
    var order = inOrder(db);
    order.verify(db).removeObjects(owner, "course-materials", List.of(path));
    order.verify(db).delete(eq(owner), eq("storage_cleanup_jobs"), anyMap());
  }
}
