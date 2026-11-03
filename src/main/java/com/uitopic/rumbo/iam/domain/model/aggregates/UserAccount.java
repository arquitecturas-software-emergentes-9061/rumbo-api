package com.uitopic.rumbo.iam.domain.model.aggregates;

import com.uitopic.rumbo.iam.domain.model.events.UserAccountLockedEvent;
import com.uitopic.rumbo.iam.domain.model.events.UserRegisteredEvent;
import com.uitopic.rumbo.iam.domain.model.valueobjects.AccountStatus;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.LockoutPolicy;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordHash;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root for the account of a Rumbo user.
 * It guarantees that only an ACTIVE account, or a LOCKED one whose lock already expired, can sign in; that the
 * account locks when it reaches the failed-attempt threshold; and that status transitions stay valid.
 * It holds no personal data: name, phone and photo belong to Profiles and Preferences.
 */
public class UserAccount {
  private final UUID id;
  private final EmailAddress email;
  private PasswordHash passwordHash;
  private final UserRole role;
  private AccountStatus status;
  private int failedLoginAttempts;
  private Instant lockedUntil;
  private UUID clinicId;
  private final Instant createdAt;
  private Instant updatedAt;
  private final List<Object> domainEvents = new ArrayList<>();

  private UserAccount(UUID id, EmailAddress email, PasswordHash passwordHash, UserRole role, AccountStatus status,
                      int failedLoginAttempts, Instant lockedUntil, UUID clinicId, Instant createdAt, Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.email = Objects.requireNonNull(email, "email is required");
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash is required");
    this.role = Objects.requireNonNull(role, "role is required");
    this.status = Objects.requireNonNull(status, "status is required");
    if (failedLoginAttempts < 0) throw new IllegalArgumentException("failedLoginAttempts cannot be negative");
    this.failedLoginAttempts = failedLoginAttempts;
    this.lockedUntil = lockedUntil;
    this.clinicId = clinicId;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
  }

  /**
   * Registers a new ACTIVE account and records {@link UserRegisteredEvent}.
   * {@code fullName} and {@code clinicName} are not stored here: they travel in the event for the contexts that own them.
   */
  public static UserAccount register(UUID id, EmailAddress email, PasswordHash passwordHash, UserRole role,
                                     String fullName, String clinicName, Instant now) {
    if (role == UserRole.CLINIC_ADMIN && (clinicName == null || clinicName.isBlank())) {
      throw new IllegalArgumentException("A clinic administrator must register a clinic name");
    }
    UserAccount account = new UserAccount(id, email, passwordHash, role, AccountStatus.ACTIVE, 0, null, null, now, now);
    account.domainEvents.add(new UserRegisteredEvent(id, email.value(), role.name(), fullName,
      role == UserRole.CLINIC_ADMIN ? clinicName.trim() : null, now));
    return account;
  }

  /** Rebuilds an account read from persistence. It records no events. */
  public static UserAccount restore(UUID id, EmailAddress email, PasswordHash passwordHash, UserRole role,
                                    AccountStatus status, int failedLoginAttempts, Instant lockedUntil, UUID clinicId,
                                    Instant createdAt, Instant updatedAt) {
    return new UserAccount(id, email, passwordHash, role, status, failedLoginAttempts, lockedUntil, clinicId,
      createdAt, updatedAt);
  }

  /** {@code true} while a lock is in force; an expired lock no longer counts. */
  public boolean isLocked(Instant now) {
    return status == AccountStatus.LOCKED && lockedUntil != null && lockedUntil.isAfter(now);
  }

  public boolean isDisabled() { return status == AccountStatus.DISABLED; }

  public boolean canSignIn(Instant now) { return !isDisabled() && !isLocked(now); }

  /**
   * Counts a failed attempt. When the policy threshold is reached the account locks and
   * {@link UserAccountLockedEvent} is recorded. Attempts made before an expired lock are not carried over.
   */
  public void registerFailedLogin(LockoutPolicy policy, Instant now) {
    Objects.requireNonNull(policy, "policy is required");
    if (isDisabled() || isLocked(now)) return;
    if (status == AccountStatus.LOCKED) clearLock();
    failedLoginAttempts++;
    if (policy.isReachedBy(failedLoginAttempts)) {
      status = AccountStatus.LOCKED;
      lockedUntil = policy.lockUntil(now);
      domainEvents.add(new UserAccountLockedEvent(id, lockedUntil, now));
    }
    updatedAt = now;
  }

  /** Resets the failure counter after a correct password and lifts an expired lock. */
  public void registerSuccessfulLogin(Instant now) {
    if (!canSignIn(now)) throw new IllegalStateException("The account cannot sign in");
    if (failedLoginAttempts == 0 && status == AccountStatus.ACTIVE) return;
    clearLock();
    updatedAt = now;
  }

  private void clearLock() {
    status = AccountStatus.ACTIVE;
    failedLoginAttempts = 0;
    lockedUntil = null;
  }

  /** Returns and clears the events recorded since the aggregate was loaded, so they are published only once. */
  public List<Object> pullDomainEvents() {
    List<Object> events = List.copyOf(domainEvents);
    domainEvents.clear();
    return events;
  }

  public UUID id() { return id; }
  public EmailAddress email() { return email; }
  public PasswordHash passwordHash() { return passwordHash; }
  public UserRole role() { return role; }
  public AccountStatus status() { return status; }
  public int failedLoginAttempts() { return failedLoginAttempts; }
  public Optional<Instant> lockedUntil() { return Optional.ofNullable(lockedUntil); }
  public Optional<UUID> clinicId() { return Optional.ofNullable(clinicId); }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }
}
