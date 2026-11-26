package com.uitopic.rumbo.subscriptions.interfaces.acl;

public record QuotaDecision(Status status, Integer limit) {
  public enum Status { ALLOWED, SUBSCRIPTION_REQUIRED, QUOTA_EXCEEDED }
}
