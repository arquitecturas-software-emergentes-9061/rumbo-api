package com.uitopic.rumbo.subscriptions.domain.exceptions;

public class PaymentOrderNotFoundException extends RuntimeException {
  public PaymentOrderNotFoundException() { super("PAYMENT_ORDER_NOT_FOUND"); }
  public String code() { return "PAYMENT_ORDER_NOT_FOUND"; }
}
