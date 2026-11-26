package com.uitopic.rumbo.subscriptions.domain.exceptions;

import java.util.Map;

public class InvalidBillingDetailsException extends IllegalArgumentException {
  private final Map<String, String> errors;
  public InvalidBillingDetailsException(Map<String, String> errors) {
    super("Invalid billing details");
    this.errors = Map.copyOf(errors);
  }
  public Map<String, String> errors() { return errors; }
}
