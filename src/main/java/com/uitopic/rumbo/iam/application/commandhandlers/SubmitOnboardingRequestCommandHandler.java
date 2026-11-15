package com.uitopic.rumbo.iam.application.commandhandlers;

import com.uitopic.rumbo.iam.domain.model.aggregates.OnboardingRequest;
import com.uitopic.rumbo.iam.domain.model.commands.SubmitOnboardingRequestCommand;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.repositories.OnboardingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Stores a demo or onboarding request from the landing page (PR-4) and returns it with its identifier. */
@Service
public class SubmitOnboardingRequestCommandHandler {
  private final OnboardingRequestRepository requests;
  private final Clock clock;

  public SubmitOnboardingRequestCommandHandler(OnboardingRequestRepository requests, Clock clock) {
    this.requests = requests;
    this.clock = clock;
  }

  @Transactional
  public OnboardingRequest handle(SubmitOnboardingRequestCommand command) {
    OnboardingRequest request = OnboardingRequest.submit(UUID.randomUUID(), command.type(), command.requesterName(),
      new EmailAddress(command.email()), command.clinicName(), command.requesterRole(), command.sitesRange(),
      command.message(), command.privacyConsent(), clock.instant());
    return requests.save(request);
  }
}
