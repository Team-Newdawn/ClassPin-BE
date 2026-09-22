package com.ohpin.folder.entity;

import com.ohpin.shared.domain.Rules;

public record Folder(String name, Integer colorIndex) {
    public Folder {
        name = Rules.text(name, "Folder name", 1, 80);
        Rules.check(colorIndex != null && colorIndex >= 0 && colorIndex < 6, "Invalid folder color");
    }
}
