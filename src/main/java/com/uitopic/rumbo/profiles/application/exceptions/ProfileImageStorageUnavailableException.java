package com.uitopic.rumbo.profiles.application.exceptions;

public class ProfileImageStorageUnavailableException extends RuntimeException {
  public static final String CODE = "PROFILE_IMAGE_STORAGE_UNAVAILABLE";
  public ProfileImageStorageUnavailableException() { super("Profile image storage is unavailable"); }
}
