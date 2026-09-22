package com.ohpin.lecture.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.lecture.entity.LectureSettings;
import com.ohpin.lecture.repository.LectureRepository;
import com.ohpin.shared.domain.Rules;
import com.ohpin.shared.security.Caller;

import java.util.*;

import org.springframework.stereotype.Service;

@Service
public class LectureService {
    private final LectureRepository repository;

    public LectureService(LectureRepository repository) {
        this.repository = repository;
    }

    public JsonNode owned(Caller c) {
        return repository.ownedGraph(c);
    }

    public JsonNode join(Caller c, String code) {
        String normalized = Rules.text(code, "Join code", 6, 10).toUpperCase(Locale.ROOT);
        Rules.check(normalized.matches("[A-Z0-9]{6,10}"), "Invalid join code");
        return repository.liveGraph(c, "join_code", normalized);
    }

    public JsonNode live(Caller c, UUID id) {
        return repository.liveGraph(c, "id", id.toString());
    }

    public JsonNode state(Caller c, UUID id) {
        return repository.state(c, id);
    }

    public void update(Caller c, UUID id, Map<String, Object> values) {
        LectureSettings.validate(values);
        repository.update(c, id, values);
    }

    public void move(Caller c, UUID id, UUID folderId) {
        repository.move(c, id, folderId);
    }

    public JsonNode delete(Caller c, UUID id) {
        return repository.delete(c, id);
    }
}
