package io.github.aidebt.core;

import java.util.Locale;

public record SourceUnit(String path, String content, Language language) {
  public SourceUnit {
    if (path == null || path.isBlank()) throw new IllegalArgumentException("path is required");
    if (content == null) throw new IllegalArgumentException("content is required");
    if (language == null) throw new IllegalArgumentException("language is required");
  }

  public static boolean supports(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    return lower.endsWith(".py");
  }

  public static Language languageOf(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".py")) return Language.PYTHON;
    throw new IllegalArgumentException("Unsupported source file: " + path);
  }

  public enum Language { PYTHON }
}
