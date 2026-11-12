package com.uitopic.rumbo.profiles.domain.model.commands;

import java.util.UUID;

public record UpdateProfileCommand(UUID userId, String fullName, String phoneNumber) {}
