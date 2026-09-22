package com.ohpin.experience.dto;

import com.ohpin.experience.entity.Experience;

public record ExperienceRequest(String code, String experience, String improvement) {
    public Experience toEntity() {
        return new Experience(code, experience, improvement);
    }
}
