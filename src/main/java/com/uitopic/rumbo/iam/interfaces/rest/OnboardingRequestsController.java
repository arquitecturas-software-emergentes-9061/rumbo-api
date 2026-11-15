package com.uitopic.rumbo.iam.interfaces.rest;

import com.uitopic.rumbo.iam.application.commandhandlers.SubmitOnboardingRequestCommandHandler;
import com.uitopic.rumbo.iam.domain.model.aggregates.OnboardingRequest;
import com.uitopic.rumbo.iam.domain.model.commands.SubmitOnboardingRequestCommand;
import com.uitopic.rumbo.iam.interfaces.rest.resources.OnboardingRequestResponse;
import com.uitopic.rumbo.iam.interfaces.rest.resources.OnboardingRequestSubmission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Demo and onboarding requests from the landing page (PR-4). Public: the clinic has no account yet. */
@RestController
@RequestMapping(value = "/api/v1/onboarding-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Onboarding requests", description = "Requests for a demo or to start using Rumbo, sent from the landing")
public class OnboardingRequestsController {
  private final SubmitOnboardingRequestCommandHandler submit;

  public OnboardingRequestsController(SubmitOnboardingRequestCommandHandler submit) {
    this.submit = submit;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Store a demo or onboarding request; it needs the privacy consent of the requester")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Request stored; the body is its receipt"),
    @ApiResponse(responseCode = "400", description = "Invalid field or missing privacy consent", content = @Content)
  })
  public ResponseEntity<OnboardingRequestResponse> submit(@Valid @RequestBody OnboardingRequestSubmission body) {
    OnboardingRequest request = submit.handle(new SubmitOnboardingRequestCommand(body.type(), body.fullName(),
      body.email(), body.clinicName(), body.role(), body.sitesRange(), body.message(), body.privacyConsent()));
    return ResponseEntity.created(URI.create("/api/v1/onboarding-requests/" + request.id()))
      .body(OnboardingRequestResponse.from(request));
  }
}
