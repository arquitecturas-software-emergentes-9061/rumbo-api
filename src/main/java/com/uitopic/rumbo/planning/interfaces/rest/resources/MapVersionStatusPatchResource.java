package com.uitopic.rumbo.planning.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Only the DRAFT to PUBLISHED transition is accepted by this endpoint. */
public record MapVersionStatusPatchResource(
    @NotBlank @Pattern(regexp = "PUBLISHED") String status) {}
