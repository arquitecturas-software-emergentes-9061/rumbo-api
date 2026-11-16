package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Complementary data of a navigation event. Only the keys in {@link #ALLOWED_KEYS} are accepted, and only as short
 * text, numbers or booleans: this is what keeps telemetry anonymized, since a client cannot slip a name, an email or
 * a free-text note into the analytics store.
 *
 * @param values immutable key-value pairs
 */
public record EventMetadata(Map<String, Object> values) {
  /** Keys the dashboard metrics use. Add a key here, with its meaning, before a client starts sending it. */
  public static final Set<String> ALLOWED_KEYS = Set.of(
    "destinationNodeReference", // NAVIGATION_STARTED: destination chosen by the visitor
    "durationSeconds",          // NAVIGATION_FINISHED: time from start to finish
    "result",                   // NAVIGATION_FINISHED: ARRIVED or CANCELLED
    "deviationMeters",          // DEVIATION_DETECTED: distance from the route
    "reason",                   // ROUTE_RECALCULATED: DEVIATION, BLOCKED_PATH or USER_REQUEST
    "appVersion");
  public static final int MAX_TEXT_LENGTH = 100;
  public static final EventMetadata EMPTY = new EventMetadata(Map.of());

  public EventMetadata {
    if (values == null) values = Map.of();
    Map<String, Object> copy = new LinkedHashMap<>();
    values.forEach((key, value) -> {
      if (!ALLOWED_KEYS.contains(key)) throw new IllegalArgumentException("Metadata key '" + key + "' is not allowed");
      copy.put(key, requireSimpleValue(key, value));
    });
    values = Collections.unmodifiableMap(copy);
  }

  public Optional<Object> get(String key) { return Optional.ofNullable(values.get(key)); }

  private static Object requireSimpleValue(String key, Object value) {
    if (value instanceof Number || value instanceof Boolean) return value;
    if (value instanceof String text && !text.isBlank() && text.length() <= MAX_TEXT_LENGTH) return text;
    throw new IllegalArgumentException("Metadata '" + key + "' must be a number, a boolean or a text of at most "
      + MAX_TEXT_LENGTH + " characters");
  }
}
