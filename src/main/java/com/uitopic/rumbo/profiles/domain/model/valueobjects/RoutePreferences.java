package com.uitopic.rumbo.profiles.domain.model.valueobjects;

public record RoutePreferences(boolean avoidStairs, boolean simplerRoute, boolean voiceGuidance) {
  public static final RoutePreferences DEFAULTS = new RoutePreferences(false, false, false);
}
