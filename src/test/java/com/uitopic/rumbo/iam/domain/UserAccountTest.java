package com.uitopic.rumbo.iam.domain;

import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.events.PasswordResetRequestedEvent;
import com.uitopic.rumbo.iam.domain.model.events.UserAccountLockedEvent;
import com.uitopic.rumbo.iam.domain.model.events.UserPasswordChangedEvent;
import com.uitopic.rumbo.iam.domain.model.events.UserRegisteredEvent;
import com.uitopic.rumbo.iam.domain.model.valueobjects.AccountStatus;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.LockoutPolicy;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordHash;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountTest {
  private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
  private static final LockoutPolicy POLICY = new LockoutPolicy(3, Duration.ofMinutes(15));

  @Test
  void registeringAnAdminRecordsTheEventWithTheClinicName() {
    UserAccount account = admin();

    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.clinicId()).isEmpty();
    assertThat(account.pullDomainEvents()).singleElement().isInstanceOfSatisfying(UserRegisteredEvent.class, event -> {
      assertThat(event.email()).isEqualTo("ana@clinica.pe");
      assertThat(event.role()).isEqualTo("CLINIC_ADMIN");
      assertThat(event.fullName()).isEqualTo("Ana Torres");
      assertThat(event.clinicName()).isEqualTo("Clinica Central");
    });
    assertThat(account.pullDomainEvents()).as("events are pulled once").isEmpty();
  }

  @Test
  void anAdminCannotRegisterWithoutAClinic() {
    assertThatThrownBy(() -> UserAccount.register(UUID.randomUUID(), new EmailAddress("ana@clinica.pe"),
      new PasswordHash("hash"), UserRole.CLINIC_ADMIN, "Ana Torres", " ", NOW))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPatientDoesNotCarryAClinicName() {
    UserAccount patient = UserAccount.register(UUID.randomUUID(), new EmailAddress("luis@mail.com"),
      new PasswordHash("hash"), UserRole.PATIENT, "Luis Rojas", "Ignored", NOW);

    assertThat(patient.pullDomainEvents()).singleElement().isInstanceOfSatisfying(UserRegisteredEvent.class,
      event -> assertThat(event.clinicName()).isNull());
  }

  @Test
  void locksWhenTheThresholdIsReached() {
    UserAccount account = admin();
    account.pullDomainEvents();

    account.registerFailedLogin(POLICY, NOW);
    account.registerFailedLogin(POLICY, NOW);
    assertThat(account.isLocked(NOW)).isFalse();
    account.registerFailedLogin(POLICY, NOW);

    assertThat(account.isLocked(NOW)).isTrue();
    assertThat(account.canSignIn(NOW)).isFalse();
    assertThat(account.lockedUntil()).contains(NOW.plus(Duration.ofMinutes(15)));
    assertThat(account.pullDomainEvents()).singleElement().isInstanceOf(UserAccountLockedEvent.class);
  }

  @Test
  void attemptsDuringALockAreNotCounted() {
    UserAccount account = lockedAccount();

    account.registerFailedLogin(POLICY, NOW.plusSeconds(60));

    assertThat(account.failedLoginAttempts()).isEqualTo(3);
    assertThat(account.lockedUntil()).contains(NOW.plus(Duration.ofMinutes(15)));
  }

  @Test
  void anExpiredLockStartsTheCountAgain() {
    UserAccount account = lockedAccount();
    Instant later = NOW.plus(Duration.ofMinutes(16));

    assertThat(account.canSignIn(later)).isTrue();
    account.registerFailedLogin(POLICY, later);

    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.failedLoginAttempts()).isEqualTo(1);
  }

  @Test
  void aSuccessfulLoginClearsTheFailures() {
    UserAccount account = admin();
    account.registerFailedLogin(POLICY, NOW);

    account.registerSuccessfulLogin(NOW);

    assertThat(account.failedLoginAttempts()).isZero();
    assertThat(account.lockedUntil()).isEmpty();
  }

  @Test
  void aLockedAccountCannotRegisterASuccessfulLogin() {
    UserAccount account = lockedAccount();

    assertThatThrownBy(() -> account.registerSuccessfulLogin(NOW)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aDisabledAccountIgnoresFailedAttempts() {
    UserAccount account = UserAccount.restore(UUID.randomUUID(), new EmailAddress("ana@clinica.pe"),
      new PasswordHash("hash"), UserRole.CLINIC_ADMIN, AccountStatus.DISABLED, 0, null, null, NOW, NOW);

    account.registerFailedLogin(POLICY, NOW);

    assertThat(account.failedLoginAttempts()).isZero();
    assertThat(account.canSignIn(NOW)).isFalse();
  }

  @Test
  void requestingARecoveryRecordsTheLinkForTheEmail() {
    UserAccount account = admin();
    account.pullDomainEvents();
    Instant expiresAt = NOW.plus(Duration.ofMinutes(30));

    account.requestPasswordReset("raw-token", expiresAt, NOW);

    assertThat(account.pullDomainEvents()).singleElement().isInstanceOfSatisfying(PasswordResetRequestedEvent.class,
      event -> {
        assertThat(event.email()).isEqualTo("ana@clinica.pe");
        assertThat(event.resetToken()).isEqualTo("raw-token");
        assertThat(event.expiresAt()).isEqualTo(expiresAt);
        assertThat(event.toString()).as("the secret never reaches the logs").doesNotContain("raw-token");
      });
  }

  @Test
  void changingThePasswordLiftsALockAndRecordsTheChange() {
    UserAccount account = lockedAccount();
    account.pullDomainEvents();

    account.changePassword(new PasswordHash("new-hash"), NOW.plusSeconds(60));

    assertThat(account.passwordHash()).isEqualTo(new PasswordHash("new-hash"));
    assertThat(account.canSignIn(NOW.plusSeconds(60))).isTrue();
    assertThat(account.failedLoginAttempts()).isZero();
    assertThat(account.pullDomainEvents()).singleElement().isInstanceOf(UserPasswordChangedEvent.class);
  }

  @Test
  void aDisabledAccountCannotRecoverNorChangeItsPassword() {
    UserAccount account = UserAccount.restore(UUID.randomUUID(), new EmailAddress("ana@clinica.pe"),
      new PasswordHash("hash"), UserRole.CLINIC_ADMIN, AccountStatus.DISABLED, 0, null, null, NOW, NOW);

    assertThat(account.canRecoverPassword()).isFalse();
    assertThatThrownBy(() -> account.requestPasswordReset("raw-token", NOW.plusSeconds(60), NOW))
      .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> account.changePassword(new PasswordHash("new-hash"), NOW))
      .isInstanceOf(IllegalStateException.class);
  }

  private static UserAccount admin() {
    return UserAccount.register(UUID.randomUUID(), new EmailAddress(" Ana@Clinica.pe "), new PasswordHash("hash"),
      UserRole.CLINIC_ADMIN, "Ana Torres", "Clinica Central", NOW);
  }

  private static UserAccount lockedAccount() {
    UserAccount account = admin();
    for (int attempt = 0; attempt < POLICY.maxFailedAttempts(); attempt++) account.registerFailedLogin(POLICY, NOW);
    return account;
  }
}
