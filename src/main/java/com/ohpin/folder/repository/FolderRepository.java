package com.ohpin.folder.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.folder.entity.Folder;
import com.ohpin.shared.security.Caller;

import java.util.UUID;

public interface FolderRepository {
    JsonNode list(Caller caller);

    JsonNode create(Caller caller, Folder folder);

    void rename(Caller caller, UUID id, String name);

    void delete(Caller caller, UUID id);
}
