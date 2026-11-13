package com.uitopic.rumbo.subscriptions.application.exceptions;

public class PaymentsUnavailableException extends RuntimeException {
  public PaymentsUnavailableException() { super("PAYMENTS_UNAVAILABLE"); }
  public String code() { return "PAYMENTS_UNAVAILABLE"; }
}
