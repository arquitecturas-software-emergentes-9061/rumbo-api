package com.uitopic.rumbo.profiles.domain.exceptions;

public class ProfileNotFoundException extends ProfilesException {
  public static final String CODE = "PROFILE_NOT_FOUND";
  public ProfileNotFoundException() { super(CODE, "Profile was not found"); }
}
