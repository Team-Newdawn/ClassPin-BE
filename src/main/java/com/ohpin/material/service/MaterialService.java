package com.ohpin.material.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.material.dto.AppendSlideRequest;
import com.ohpin.material.entity.Material;
import com.ohpin.material.entity.SourcePath;
import com.ohpin.material.repository.MaterialRepository;
import com.ohpin.shared.domain.Rules;
import com.ohpin.shared.security.Caller;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MaterialService {
  private final MaterialRepository repository;

  public MaterialService(MaterialRepository repository) {
    this.repository = repository;
  }

  public void create(Caller c, Material draft) {
    draft.validateOwner(c.id());
    repository.create(c, draft);
  }

  public JsonNode slides(Caller c, UUID id) {
    return repository.slides(c, id);
  }

  public JsonNode append(Caller c, UUID version, List<AppendSlideRequest> slides) {
    Rules.check(
        slides != null && !slides.isEmpty() && slides.size() <= 20,
        "Append between 1 and 20 slides");
    Set<UUID> ids = new HashSet<>();
    var rows = new ArrayList<Map<String, Object>>();
    for (var slide : slides) {
      Rules.check(
          slide != null && slide.id() != null && ids.add(slide.id()), "Unique slide IDs required");
      String path = SourcePath.owned(c.id(), slide.imagePath());
      Rules.check(path.matches(".+\\.(jpg|jpeg|png|webp)"), "Invalid image format");
      rows.add(Map.of("id", slide.id(), "image_path", path));
    }
    return repository.append(c, version, rows);
  }

  public JsonNode deleteSlide(Caller c, UUID id) {
    return repository.deleteSlide(c, id);
  }

  public void saveNote(Caller c, UUID id, String body) {
    Rules.check(
        body != null && body.codePointCount(0, body.length()) <= 10000,
        "Notes must not exceed 10000 characters");
    repository.saveNote(c, id, body);
  }
}
