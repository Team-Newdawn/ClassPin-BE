package com.ohpin.folder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.folder.entity.Folder;
import com.ohpin.folder.repository.FolderRepository;
import com.ohpin.shared.security.Caller;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class FolderService {
    private final FolderRepository repository;

    public FolderService(FolderRepository repository) {
        this.repository = repository;
    }

    public JsonNode list(Caller c) {
        return repository.list(c);
    }

    public JsonNode create(Caller c, Folder folder) {
        return repository.create(c, folder);
    }

    public void update(Caller c, UUID id, Map<String, Object> fields) {
        repository.update(c, id, fields);
    }

    public void delete(Caller c, UUID id) {
        repository.delete(c, id);
    }
}
