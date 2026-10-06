package com.ohpin.lecture.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.shared.security.Caller;

import java.util.*;

public interface LectureRepository {
    JsonNode ownedGraph(Caller c);

    JsonNode liveGraph(Caller c, String column, String value);

    String audienceStatus(Caller c, UUID id, String joinCode);

    JsonNode state(Caller c, UUID id);

    JsonNode participantState(Caller c, UUID id);

    void update(Caller c, UUID id, Map<String, Object> fields);

    void move(Caller c, UUID courseId, UUID folderId);

    JsonNode delete(Caller c, UUID courseId);
}
