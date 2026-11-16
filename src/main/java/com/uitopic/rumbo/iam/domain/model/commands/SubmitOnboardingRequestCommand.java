package com.uitopic.rumbo.iam.domain.model.commands;

import com.uitopic.rumbo.iam.domain.model.valueobjects.ClinicSitesRange;
import com.uitopic.rumbo.iam.domain.model.valueobjects.OnboardingRequestType;

/**
 * A clinic asks for a demo or to start using Rumbo from the landing page (PR-4). Nobody is signed in yet, so the
 * request carries its own contact data.
 *
 * @param sitesRange optional; {@code null} when the requester did not say
 * @param message    optional free text
 */
public record SubmitOnboardingRequestCommand(OnboardingRequestType type, String requesterName, String email,
                                             String clinicName, String requesterRole, ClinicSitesRange sitesRange,
                                             String message, boolean privacyConsent) {
}
