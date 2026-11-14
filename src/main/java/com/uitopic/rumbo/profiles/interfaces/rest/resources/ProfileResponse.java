package com.uitopic.rumbo.profiles.interfaces.rest.resources;

import java.util.UUID;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.RoutePreferences;

public record ProfileResponse(UUID userId, String fullName, String role, String phoneNumber, String imageUrl,
    RoutePreferences preferences) {}
