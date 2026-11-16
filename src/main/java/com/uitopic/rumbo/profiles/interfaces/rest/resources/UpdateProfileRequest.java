package com.uitopic.rumbo.profiles.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
    @NotBlank @Size(max = 255) String fullName,
    @Pattern(regexp = "^$|^\\s*$|^(?=.{6,30}$)\\+?[0-9 ()-]+$") String phoneNumber) {
  public UpdateProfileRequest {
    if (fullName != null) fullName = fullName.trim();
    if (phoneNumber != null && phoneNumber.isBlank()) phoneNumber = null;
  }
}
