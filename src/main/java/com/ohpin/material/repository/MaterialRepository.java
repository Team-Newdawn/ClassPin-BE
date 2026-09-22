package com.ohpin.material.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.material.entity.Material;
import com.ohpin.shared.security.Caller;
import java.util.*;

public interface MaterialRepository {
  void create(Caller c, Material draft);

  JsonNode slides(Caller c, UUID version);

  JsonNode append(Caller c, UUID version, List<Map<String, Object>> slides);

  JsonNode deleteSlide(Caller c, UUID id);

  void saveNote(Caller c, UUID id, String body);
}
