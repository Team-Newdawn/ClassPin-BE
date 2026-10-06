package com.ohpin.folder.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.folder.entity.Folder;
import com.ohpin.shared.security.Caller;

import java.util.Map;
import java.util.UUID;

public interface FolderRepository {
    JsonNode list(Caller caller);

    JsonNode create(Caller caller, Folder folder);

    void update(Caller caller, UUID id, Map<String, Object> fields);

    void delete(Caller caller, UUID id);
}
