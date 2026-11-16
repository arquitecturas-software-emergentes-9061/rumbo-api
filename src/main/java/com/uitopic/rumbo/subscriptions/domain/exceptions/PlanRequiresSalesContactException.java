package com.uitopic.rumbo.subscriptions.domain.exceptions;

public class PlanRequiresSalesContactException extends RuntimeException {
  public PlanRequiresSalesContactException() { super("PLAN_REQUIRES_SALES_CONTACT"); }
  public String code() { return "PLAN_REQUIRES_SALES_CONTACT"; }
}
