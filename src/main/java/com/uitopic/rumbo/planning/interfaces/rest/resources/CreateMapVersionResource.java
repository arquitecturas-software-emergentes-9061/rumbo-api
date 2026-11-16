package com.uitopic.rumbo.planning.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Selects the current published version to copy into a new draft. */
public record CreateMapVersionResource(@NotNull UUID sourceVersionId) {}
