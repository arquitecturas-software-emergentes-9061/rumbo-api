package com.uitopic.rumbo.profiles.application.exceptions;

public class ProfileImageStorageFailedException extends RuntimeException {
  public static final String CODE = "PROFILE_IMAGE_STORAGE_FAILED";
  public ProfileImageStorageFailedException() { super("Profile image storage failed"); }
}
