package com.uitopic.rumbo.iam.domain.exceptions;

/**
 * The refresh token is unknown, was already rotated, or belongs to a revoked or expired session (TS-32). The client
 * must sign in again. One message covers every case so a stolen token reveals nothing.
 */
public class InvalidRefreshTokenException extends IamException {
  public InvalidRefreshTokenException() {
    super("INVALID_REFRESH_TOKEN", "The session is no longer valid, sign in again");
  }
}
