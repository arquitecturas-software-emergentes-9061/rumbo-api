package com.uitopic.rumbo.subscriptions.application.exceptions;

public class PaymentProviderException extends RuntimeException {
  public PaymentProviderException() { super("PAYMENT_PROVIDER_ERROR"); }
  public String code() { return "PAYMENT_PROVIDER_ERROR"; }
}
