package com.ohpin.material.entity;

import com.ohpin.shared.domain.Rules;
import java.util.*;

public final class SourcePath {
  public static final long MAX_BYTES = 1024L * 1024 * 1024;

  private SourcePath() {}

  public static String extension(String name) {
    Rules.check(name != null, "File name is required");
    String lower = name.toLowerCase(Locale.ROOT);
    return List.of(".pdf", ".pptx", ".ppt").stream()
        .filter(lower::endsWith)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Only PDF, PPT and PPTX are supported"));
  }

  public static String owned(UUID owner, String path) {
    Rules.check(
        path != null && path.startsWith(owner + "/") && path.length() <= 1024,
        "Storage path must belong to the authenticated owner");
    Rules.check(
        !path.contains("%")
            && !path.contains("\\")
            && !path.contains("?")
            && !path.contains("#")
            && path.chars().noneMatch(Character::isISOControl),
        "Invalid storage path");
    for (String part : path.split("/", -1))
      Rules.check(
          !part.isBlank() && !part.equals(".") && !part.equals(".."), "Invalid storage path");
    return path;
  }

  public static String validate(UUID owner, String path, String fileName) {
    owned(owner, path);
    Rules.check(
        path.endsWith(extension(fileName)), "Source extension does not match the file name");
    return path;
  }
}
