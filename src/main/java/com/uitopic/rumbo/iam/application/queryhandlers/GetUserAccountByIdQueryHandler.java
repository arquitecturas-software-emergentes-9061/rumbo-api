package com.uitopic.rumbo.iam.application.queryhandlers;

import com.uitopic.rumbo.iam.domain.exceptions.UserAccountNotFoundException;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.queries.GetUserAccountByIdQuery;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetUserAccountByIdQueryHandler {
  private final UserAccountRepository accounts;

  public GetUserAccountByIdQueryHandler(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Transactional(readOnly = true)
  public UserAccount handle(GetUserAccountByIdQuery query) {
    return accounts.findById(query.userId()).orElseThrow(() -> new UserAccountNotFoundException(query.userId()));
  }
}
