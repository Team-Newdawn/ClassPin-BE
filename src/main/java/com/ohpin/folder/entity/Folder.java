package com.ohpin.folder.entity;

import com.ohpin.shared.domain.Rules;
import java.util.Set;

public record Folder(String name, Integer colorIndex, String purpose, String purposeLabel) {
    private static final Set<String> PURPOSES =
            Set.of("qa", "feedback", "education", "brainstorming", "other");

    public Folder {
        name = Rules.text(name, "Folder name", 1, 80);
        Rules.check(colorIndex != null && colorIndex >= 0 && colorIndex < 6, "Invalid folder color");
        purpose = validatePurpose(purpose);
        purposeLabel = "other".equals(purpose) ? normalizePurposeLabel(purposeLabel) : null;
    }

    public static String validatePurpose(String purpose) {
        Rules.check(PURPOSES.contains(purpose), "Invalid folder purpose");
        return purpose;
    }

    public static String normalizePurposeLabel(String purposeLabel) {
        if (purposeLabel == null || purposeLabel.isBlank()) return null;
        String normalized = purposeLabel.strip();
        Rules.check(
                normalized.codePointCount(0, normalized.length()) <= 40,
                "Folder purpose label must contain at most 40 characters");
        return normalized;
    }
}
