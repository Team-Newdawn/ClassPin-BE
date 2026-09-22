package com.ohpin.lecture.entity;

import com.ohpin.shared.domain.Rules;

import java.util.*;

public final class LectureSettings {
    private LectureSettings() {
    }

    private static final Set<String> BOOLEANS =
            Set.of("presentation_interactions", "show_question_pins", "show_presentation_qr");
    public static final Set<String> POSITIONS =
            Set.of("top-left", "top-right", "bottom-left", "bottom-right");

    public static void validate(Map<String, Object> fields) {
        Rules.check(fields != null && !fields.isEmpty(), "Lecture settings are required");
        fields.forEach(
                (key, value) -> {
                    if (BOOLEANS.contains(key))
                        Rules.check(value instanceof Boolean, "Invalid boolean setting");
                    else
                        switch (key) {
                            case "current_page" ->
                                    Rules.check(value instanceof Integer page && page >= 0, "Invalid page index");
                            case "status" -> Rules.check(
                                    value instanceof String status && Set.of("live", "ended").contains(status),
                                    "Invalid lecture status");
                            case "presentation_qr_position" -> Rules.check(
                                    value instanceof String position && POSITIONS.contains(position),
                                    "Invalid QR position");
                            case "question_categories" -> categories(value);
                            default -> throw new IllegalArgumentException("Unsupported lecture field");
                        }
                });
    }

    public static void categories(Object value) {
        Rules.check(value instanceof Map<?, ?>, "Categories must be an object");
        Map<?, ?> entries = (Map<?, ?>) value;
        Rules.check(entries.size() <= 50, "Too many stored categories");
        int active = 0, visible = 0;
        Set<String> builtIn =
                Set.of(
                        "concept",
                        "why",
                        "example",
                        "error",
                        "important",
                        "praise",
                        "improve",
                        "confusing",
                        "bug",
                        "idea");
        for (var entry : entries.entrySet()) {
            Rules.check(
                    entry.getKey() instanceof String key
                            && key.matches("[a-z0-9][a-z0-9-]{0,63}")
                            && entry.getValue() instanceof Map<?, ?>,
                    "Invalid category");
            Map<?, ?> setting = (Map<?, ?>) entry.getValue();
            Rules.check(
                    setting.get("label") instanceof String
                            && setting.get("enabled") instanceof Boolean
                            && setting.get("archived") instanceof Boolean,
                    "Invalid category fields");
            String label = (String) setting.get("label");
            Rules.check(
                    label.equals(label.strip())
                            && label.codePointCount(0, label.length()) <= 40
                            && (builtIn.contains(entry.getKey()) || !label.isEmpty()),
                    "Invalid category label");
            if (!Boolean.TRUE.equals(setting.get("archived"))) {
                visible++;
                if (Boolean.TRUE.equals(setting.get("enabled"))) active++;
            }
        }
        Rules.check(visible <= 20 && active >= 1, "Between 1 and 20 categories are required");
    }
}
