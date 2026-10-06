package com.ohpin.lecture.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AudienceLectureResult(int status, JsonNode body) {}
