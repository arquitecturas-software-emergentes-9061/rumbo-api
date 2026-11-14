package com.uitopic.rumbo.profiles.domain.model.commands;

import java.util.UUID;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImageFile;

public record ReplaceProfileImageCommand(UUID userId, ProfileImageFile file) {}
