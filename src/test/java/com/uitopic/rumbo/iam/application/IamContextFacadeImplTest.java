package com.uitopic.rumbo.iam.application;

import com.uitopic.rumbo.iam.application.acl.IamContextFacadeImpl;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.valueobjects.AccountStatus;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordHash;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import com.uitopic.rumbo.iam.interfaces.acl.IamContextFacade.UserContactData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IamContextFacadeImplTest {
  private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final IamContextFacadeImpl facade = new IamContextFacadeImpl(accounts);

  @Test
  void exportsTheContactOfAnAccount() {
    UUID id = UUID.randomUUID();
    when(accounts.findById(id)).thenReturn(Optional.of(account(id, AccountStatus.ACTIVE)));

    assertThat(facade.getUserContactById(id))
      .contains(new UserContactData(id, "luis@mail.com", "PATIENT", true));
  }

  @Test
  void aDisabledAccountIsNotReachable() {
    UUID id = UUID.randomUUID();
    when(accounts.findById(id)).thenReturn(Optional.of(account(id, AccountStatus.DISABLED)));

    assertThat(facade.getUserContactById(id)).hasValueSatisfying(contact -> assertThat(contact.reachable()).isFalse());
  }

  @Test
  void anUnknownAccountHasNoContact() {
    assertThat(facade.getUserContactById(UUID.randomUUID())).isEmpty();
  }

  private static UserAccount account(UUID id, AccountStatus status) {
    return UserAccount.restore(id, new EmailAddress("luis@mail.com"), new PasswordHash("hash"), UserRole.PATIENT,
      status, 0, null, null, NOW, NOW);
  }
}
