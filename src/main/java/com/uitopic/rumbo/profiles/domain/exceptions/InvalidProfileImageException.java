package com.uitopic.rumbo.profiles.domain.exceptions;

/** A profile image violates the content, size or declared type restrictions. */
public class InvalidProfileImageException extends ProfilesException {
  public static final String EMPTY_IMAGE = "EMPTY_IMAGE";
  public static final String IMAGE_TOO_LARGE = "IMAGE_TOO_LARGE";
  public static final String UNSUPPORTED_IMAGE_FORMAT = "UNSUPPORTED_IMAGE_FORMAT";
  public static final String IMAGE_TYPE_MISMATCH = "IMAGE_TYPE_MISMATCH";

  public InvalidProfileImageException(String code, String message) {
    super(code, message);
  }
}
