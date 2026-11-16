package com.uitopic.rumbo.iam.domain.exceptions;

import java.util.UUID;

public class UserAccountNotFoundException extends IamException {
  public UserAccountNotFoundException(UUID userId) {
    super("USER_ACCOUNT_NOT_FOUND", "User account " + userId + " does not exist");
  }
}
