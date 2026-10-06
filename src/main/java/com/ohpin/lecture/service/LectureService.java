package com.ohpin.lecture.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.lecture.entity.LectureSettings;
import com.ohpin.lecture.dto.AudienceLectureResult;
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

    public AudienceLectureResult join(Caller c, String code) {
        String normalized = Rules.text(code, "Join code", 6, 10).toUpperCase(Locale.ROOT);
        Rules.check(normalized.matches("[A-Z0-9]{6,10}"), "Invalid join code");
        return audience(c, null, normalized, false);
    }

    public AudienceLectureResult live(Caller c, UUID id) {
        return audience(c, id, null, false);
    }

    public JsonNode instructorState(Caller c, UUID id) {
        return repository.state(c, id);
    }

    public AudienceLectureResult participantState(Caller c, UUID id) {
        return audience(c, id, null, true);
    }

    public void update(Caller c, UUID id, Map<String, Object> values) {
        LectureSettings.validate(values);
        repository.update(c, id, values);
    }

    private AudienceLectureResult audience(Caller c, UUID id, String code, boolean stateOnly) {
        String status = repository.audienceStatus(c, id, code);
        if (status == null || "archived".equals(status)) return new AudienceLectureResult(404, null);
        if ("before".equals(status) || "pending".equals(status))
            return new AudienceLectureResult(204, null);
        if ("ended".equals(status)) return new AudienceLectureResult(410, null);
        if (!"live".equals(status)) return new AudienceLectureResult(404, null);
        JsonNode body = stateOnly
                ? repository.participantState(c, id)
                : repository.liveGraph(c, id == null ? "join_code" : "id", id == null ? code : id.toString());
        if (body == null) {
            String current = repository.audienceStatus(c, id, code);
            if ("before".equals(current) || "pending".equals(current))
                return new AudienceLectureResult(204, null);
            if ("ended".equals(current)) return new AudienceLectureResult(410, null);
            return new AudienceLectureResult(404, null);
        }
        return new AudienceLectureResult(200, body);
    }

    public void move(Caller c, UUID id, UUID folderId) {
        repository.move(c, id, folderId);
    }

    public JsonNode delete(Caller c, UUID id) {
        return repository.delete(c, id);
    }
}
