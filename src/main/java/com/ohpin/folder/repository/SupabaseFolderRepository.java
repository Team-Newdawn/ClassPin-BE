package com.ohpin.folder.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.folder.entity.Folder;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;

import java.util.*;

import org.springframework.stereotype.Repository;

@Repository
public class SupabaseFolderRepository implements FolderRepository {
    private final SupabaseGateway db;

    public SupabaseFolderRepository(SupabaseGateway db) {
        this.db = db;
    }

    public JsonNode list(Caller c) {
        return db.get(
                c,
                "session_folders",
                Map.of(
                        "select",
                        "id,name,created_at,color_index,purpose,purpose_label",
                        "owner_id",
                        "eq." + c.id(),
                        "order",
                        "created_at.asc"));
    }

    public JsonNode create(Caller c, Folder f) {
        var row = new LinkedHashMap<String, Object>();
        row.put("owner_id", c.id());
        row.put("name", f.name());
        row.put("color_index", f.colorIndex());
        row.put("purpose", f.purpose());
        row.put("purpose_label", f.purposeLabel());
        return SupabaseGateway.required(
                db.insert(c, "session_folders", row));
    }

    public void rename(Caller c, UUID id, String name) {
        db.patch(c, "session_folders", owned(c, id), Map.of("name", name));
    }

    public void delete(Caller c, UUID id) {
        db.delete(c, "session_folders", owned(c, id));
    }

    private Map<String, String> owned(Caller c, UUID id) {
        return Map.of("id", "eq." + id, "owner_id", "eq." + c.id(), "select", "id");
    }
}
