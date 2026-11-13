package com.uitopic.rumbo.profiles.interfaces.rest.resources;

import java.util.UUID;

public record ProfileResponse(UUID userId, String fullName, String role, String phoneNumber, String imageUrl) {}
