package com.ohpin.lecture.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;

import java.util.*;

import org.springframework.stereotype.Repository;

@Repository
public class SupabaseLectureRepository implements LectureRepository {
    private final SupabaseGateway db;

    public SupabaseLectureRepository(SupabaseGateway db) {
        this.db = db;
    }

    private static final String AUDIENCE_LECTURE =
            "id,course_id,title,join_code,status,current_page,presentation_interactions,presentation_autoplay,show_question_pins,show_presentation_qr,presentation_qr_position,question_categories,allow_question_reactions,allow_emoji_reactions,started_at,created_at";
    private static final String LECTURE = AUDIENCE_LECTURE + ",ended_at";
    private static final String SLIDE =
            "id,material_version_id,page_index,image_path,source_page_index";
    private static final String QUESTIONS =
            "questions(id,lecture_id,slide_id,category,marker,raw_text,status,reaction_count,created_at,region_anchors(kind,coords),answers(body,created_at))";

    public JsonNode ownedGraph(Caller c) {
        String select =
                "id,folder_id,lectures!inner("
                        + LECTURE
                        + ",materials!materials_lecture_id_fkey(id,file_name,created_at,material_versions(id,version_no,source_path,slides("
                        + SLIDE
                        + ",slide_instructor_notes(slide_id,body)))),"
                        + QUESTIONS
                        + ")";
        return db.get(
                c,
                "courses",
                Map.of("select", select, "owner_id", "eq." + c.id(), "lectures.status", "neq.archived"));
    }

    public JsonNode liveGraph(Caller c, String column, String value) {
        // Explicit public projection: speaker notes and author identities never enter this response.
        String select =
                AUDIENCE_LECTURE
                        + ",materials!materials_lecture_id_fkey(id,file_name,material_versions(id,version_no,source_path,slides("
                        + SLIDE
                        + ")))";
        return SupabaseGateway.first(
                db.get(
                        c, "lectures", Map.of("select", select, column, "eq." + value, "status", "eq.live")));
    }

    public JsonNode state(Caller c, UUID id) {
        return SupabaseGateway.first(
                db.get(c, "lectures", Map.of("select", LECTURE, "id", "eq." + id)));
    }

    public JsonNode participantState(Caller c, UUID id) {
        return SupabaseGateway.first(
                db.get(c, "lectures", Map.of("select", AUDIENCE_LECTURE, "id", "eq." + id)));
    }

    public String audienceStatus(Caller c, UUID id, String joinCode) {
        var params = new LinkedHashMap<String, Object>();
        params.put("target_id", id);
        params.put("target_join_code", joinCode);
        JsonNode result = db.rpc(c, "ohpin_participant_lecture_status", params);
        return result == null || result.isNull() ? null : result.asText(null);
    }

    public void update(Caller c, UUID id, Map<String, Object> fields) {
        db.rpc(c, "ohpin_update_lecture_settings", Map.of("target_lecture_id", id, "settings", fields));
    }

    public void move(Caller c, UUID id, UUID folderId) {
        var body = new HashMap<String, Object>();
        body.put("folder_id", folderId);
        db.patch(
                c, "courses", Map.of("id", "eq." + id, "owner_id", "eq." + c.id(), "select", "id"), body);
    }

    public JsonNode delete(Caller c, UUID id) {
        return db.rpc(c, "ohpin_delete_course", Map.of("target_course_id", id));
    }
}
