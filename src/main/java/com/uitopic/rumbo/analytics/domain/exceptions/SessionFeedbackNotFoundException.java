package com.uitopic.rumbo.analytics.domain.exceptions;

/**
 * The session has no feedback on the requested site. A session of another site answers the same, so the response
 * does not reveal sessions of other organizations. The API answers 404 SESSION_FEEDBACK_NOT_FOUND.
 */
public class SessionFeedbackNotFoundException extends RuntimeException {
  public static final String CODE = "SESSION_FEEDBACK_NOT_FOUND";

  public SessionFeedbackNotFoundException() {
    super("The session has no feedback on this site");
  }
}
