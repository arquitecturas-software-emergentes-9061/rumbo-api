package com.uitopic.rumbo.planning.infrastructure.persistence.jpa;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Converts directory labels and queries to the same accent-insensitive PostgreSQL tokens. */
final class DirectorySearchIndex {
  private DirectorySearchIndex() {}

  static String normalize(String value) {
    String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    return withoutAccents.replaceAll("[^\\p{Alnum}]+", " ").strip();
  }

  static String searchableText(String name, String specialty, String type) {
    return normalize(name + " " + (specialty == null ? "" : specialty) + " " + type);
  }

  static String prefixQuery(String term) {
    String normalized = normalize(term);
    if (normalized.isBlank()) return "";
    return Arrays.stream(normalized.split("\\s+"))
        .map(token -> token + ":*").collect(Collectors.joining(" & "));
  }
}
