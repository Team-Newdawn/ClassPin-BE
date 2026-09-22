package com.ohpin.material.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.material.entity.Material;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class SupabaseMaterialRepository implements MaterialRepository {
  private final SupabaseGateway db;

  public SupabaseMaterialRepository(SupabaseGateway db) {
    this.db = db;
  }

  public void create(Caller c, Material draft) {
    db.rpc(c, "ohpin_create_material", Map.of("draft", draft));
  }

  public JsonNode slides(Caller c, UUID version) {
    String select =
        "id,material_version_id,page_index,image_path,source_page_index"
            + (c.instructor() ? ",slide_instructor_notes(slide_id,body)" : "");
    return db.get(
        c,
        "slides",
        Map.of(
            "select", select, "material_version_id", "eq." + version, "order", "page_index.asc"));
  }

  public JsonNode append(Caller c, UUID version, List<Map<String, Object>> slides) {
    return db.rpc(
        c,
        "append_lecture_slides",
        Map.of("target_material_version_id", version, "new_slides", slides));
  }

  public JsonNode deleteSlide(Caller c, UUID id) {
    return SupabaseGateway.required(
        db.rpc(c, "delete_lecture_slide", Map.of("target_slide_id", id)));
  }

  public void saveNote(Caller c, UUID id, String body) {
    db.json(
        c.token(),
        "POST",
        "/rest/v1/slide_instructor_notes",
        Map.of("on_conflict", "slide_id"),
        Map.of("slide_id", id, "body", body, "updated_at", Instant.now().toString()),
        "resolution=merge-duplicates,return=minimal");
  }
}
