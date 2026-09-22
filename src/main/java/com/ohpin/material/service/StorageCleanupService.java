package com.ohpin.material.service;

import com.ohpin.material.entity.SourcePath;
import com.ohpin.shared.api.ApiFailure;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class StorageCleanupService {
  private final SupabaseGateway db;

  public StorageCleanupService(SupabaseGateway db) {
    this.db = db;
  }

  public boolean retry(Caller c) {
    try {
      return processPending(c);
    } catch (ApiFailure e) {
      return false;
    }
  }

  private boolean processPending(Caller c) {
    boolean complete = true;
    var jobs =
        db.get(
            c,
            "storage_cleanup_jobs",
            Map.of(
                "select",
                "id,bucket,object_path",
                "owner_id",
                "eq." + c.id(),
                "order",
                "created_at.asc",
                "limit",
                "100"));
    for (var job : jobs) {
      try {
        String path = SourcePath.owned(c.id(), job.path("object_path").asText());
        String bucket = job.path("bucket").asText();
        // A source may still be used by another version; never delete referenced objects.
        String table = bucket.equals("course-materials") ? "material_versions" : "slides";
        String column = bucket.equals("course-materials") ? "source_path" : "image_path";
        if (!db.get(c, table, Map.of("select", "id", column, "eq." + path, "limit", "1"))
            .isEmpty()) {
          complete = false;
          continue;
        }
        db.removeObjects(c, bucket, List.of(path));
        db.delete(
            c,
            "storage_cleanup_jobs",
            Map.of("id", "eq." + job.path("id").asText(), "select", "id"));
      } catch (ApiFailure e) {
        complete = false;
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Storage cleanup retained for retry; status={}", e.status());
      }
    }
    return complete && jobs.size() < 100;
  }
}
