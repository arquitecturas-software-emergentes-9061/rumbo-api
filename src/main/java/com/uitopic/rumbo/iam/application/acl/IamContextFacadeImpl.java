package com.uitopic.rumbo.iam.application.acl;

import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import com.uitopic.rumbo.iam.interfaces.acl.IamContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Maps IAM accounts to the contract that other bounded contexts read. */
@Service
public class IamContextFacadeImpl implements IamContextFacade {
  private final UserAccountRepository accounts;

  public IamContextFacadeImpl(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserContactData> getUserContactById(UUID userId) {
    return accounts.findById(userId).map(IamContextFacadeImpl::toData);
  }

  private static UserContactData toData(UserAccount account) {
    return new UserContactData(account.id(), account.email().value(), account.role().name(), !account.isDisabled());
  }
}
