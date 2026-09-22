package com.ohpin.experience.entity;

import com.ohpin.shared.domain.Rules;

import java.util.Locale;

public record Experience(String code, String experience, String improvement) {
    public Experience {
        code = Rules.text(code, "Join code", 6, 10).toUpperCase(Locale.ROOT);
        Rules.check(code.matches("[A-Z0-9]{6,10}"), "Invalid join code");
        experience = Rules.text(experience, "Experience", 1, 1000);
        improvement = Rules.text(improvement, "Improvement", 1, 1000);
    }
}
