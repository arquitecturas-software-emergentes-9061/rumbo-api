package com.uitopic.rumbo.iam.domain.exceptions;

/** The password does not meet the password policy (TS-26, scenario 3). */
public class WeakPasswordException extends IamException {
  public WeakPasswordException(String policyDescription) {
    super("WEAK_PASSWORD", "Password does not meet the policy: " + policyDescription);
  }
}
