package com.ohpin.folder.entity;

import com.ohpin.shared.domain.Rules;
import java.util.Set;

public record Folder(String name, Integer colorIndex, String purpose, String purposeLabel) {
    private static final Set<String> PURPOSES =
            Set.of("qa", "feedback", "education", "brainstorming", "other");

    public Folder {
        name = Rules.text(name, "Folder name", 1, 80);
        Rules.check(colorIndex != null && colorIndex >= 0 && colorIndex < 6, "Invalid folder color");
        Rules.check(PURPOSES.contains(purpose), "Invalid folder purpose");
        if (!"other".equals(purpose) || purposeLabel == null || purposeLabel.isBlank()) {
            purposeLabel = null;
        } else {
            purposeLabel = purposeLabel.strip();
            Rules.check(
                    purposeLabel.codePointCount(0, purposeLabel.length()) <= 40,
                    "Folder purpose label must contain at most 40 characters");
        }
    }
}
