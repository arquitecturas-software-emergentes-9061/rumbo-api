package com.uitopic.rumbo.subscriptions.domain.exceptions;

public class PlanNotFoundException extends RuntimeException {
  public PlanNotFoundException() { super("PLAN_NOT_FOUND"); }
  public String code() { return "PLAN_NOT_FOUND"; }
}
