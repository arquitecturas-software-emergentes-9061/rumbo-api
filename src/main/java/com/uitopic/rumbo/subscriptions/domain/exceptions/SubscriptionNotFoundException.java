package com.uitopic.rumbo.subscriptions.domain.exceptions;

public class SubscriptionNotFoundException extends RuntimeException {
  public SubscriptionNotFoundException() { super("SUBSCRIPTION_NOT_FOUND"); }
  public String code() { return "SUBSCRIPTION_NOT_FOUND"; }
}
