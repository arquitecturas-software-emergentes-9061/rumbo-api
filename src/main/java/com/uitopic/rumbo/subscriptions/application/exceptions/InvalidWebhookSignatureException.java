package com.uitopic.rumbo.subscriptions.application.exceptions;
public class InvalidWebhookSignatureException extends RuntimeException {
  public InvalidWebhookSignatureException() { super("Invalid webhook signature"); }
}
