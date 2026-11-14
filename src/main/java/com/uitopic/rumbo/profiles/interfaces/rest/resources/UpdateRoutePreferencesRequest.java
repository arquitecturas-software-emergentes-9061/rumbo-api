package com.uitopic.rumbo.profiles.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;

public record UpdateRoutePreferencesRequest(@NotNull Boolean avoidStairs, @NotNull Boolean simplerRoute,
                                            @NotNull Boolean voiceGuidance) {}
