package com.uitopic.rumbo.iam.interfaces.rest.resources;

import com.uitopic.rumbo.iam.domain.model.aggregates.OnboardingRequest;
import com.uitopic.rumbo.iam.domain.model.valueobjects.OnboardingRequestType;

import java.time.Instant;
import java.util.UUID;

/**
 * Receipt of a stored request. It only echoes what the landing needs to confirm it, never the contact data, so the
 * answer leaks nothing if it is logged by a proxy.
 */
public record OnboardingRequestResponse(UUID id, OnboardingRequestType type, Instant submittedAt) {
  public static OnboardingRequestResponse from(OnboardingRequest request) {
    return new OnboardingRequestResponse(request.id(), request.type(), request.submittedAt());
  }
}
