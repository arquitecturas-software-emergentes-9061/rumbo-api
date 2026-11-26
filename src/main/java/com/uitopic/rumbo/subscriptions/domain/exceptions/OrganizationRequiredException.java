package com.uitopic.rumbo.subscriptions.domain.exceptions;

public class OrganizationRequiredException extends RuntimeException {
  public OrganizationRequiredException() { super("Register a clinic before checkout"); }
}
