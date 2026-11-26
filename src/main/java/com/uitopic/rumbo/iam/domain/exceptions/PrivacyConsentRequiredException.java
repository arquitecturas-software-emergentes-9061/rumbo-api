package com.uitopic.rumbo.iam.domain.exceptions;

/** The requester did not accept the privacy policy, so their contact data cannot be stored. */
public class PrivacyConsentRequiredException extends IamException {
  public static final String CODE = "PRIVACY_CONSENT_REQUIRED";

  public PrivacyConsentRequiredException() {
    super(CODE, "The privacy policy must be accepted before the request is stored");
  }
}
