package com.uitopic.rumbo.iam.application.commandhandlers;

import com.uitopic.rumbo.iam.application.ports.SecureTokenGenerator;
import com.uitopic.rumbo.iam.application.results.AuthenticatedUser;
import com.uitopic.rumbo.iam.application.services.SessionCredentialsIssuer;
import com.uitopic.rumbo.iam.application.services.SessionRevocationService;
import com.uitopic.rumbo.iam.domain.exceptions.InvalidRefreshTokenException;
import com.uitopic.rumbo.iam.domain.model.aggregates.AuthSession;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.commands.RefreshSessionCommand;
import com.uitopic.rumbo.iam.domain.repositories.AuthSessionRepository;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Rotates a session (TS-32): the refresh token works once, and the response carries the next one. If the account was
 * locked or disabled meanwhile, the session is closed instead of renewed.
 */
@Service
public class RefreshSessionCommandHandler {
  private final AuthSessionRepository sessions;
  private final UserAccountRepository accounts;
  private final SecureTokenGenerator tokenGenerator;
  private final SessionCredentialsIssuer credentialsIssuer;
  private final SessionRevocationService revocation;
  private final Clock clock;

  public RefreshSessionCommandHandler(AuthSessionRepository sessions, UserAccountRepository accounts,
                                      SecureTokenGenerator tokenGenerator, SessionCredentialsIssuer credentialsIssuer,
                                      SessionRevocationService revocation, Clock clock) {
    this.sessions = sessions;
    this.accounts = accounts;
    this.tokenGenerator = tokenGenerator;
    this.credentialsIssuer = credentialsIssuer;
    this.revocation = revocation;
    this.clock = clock;
  }

  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public AuthenticatedUser handle(RefreshSessionCommand command) {
    if (command.refreshToken() == null || command.refreshToken().isBlank()) throw new InvalidRefreshTokenException();
    Instant now = clock.instant();
    AuthSession session = sessions.findByRefreshTokenHash(tokenGenerator.digest(command.refreshToken()))
      .filter(found -> found.isActive(now))
      .orElseThrow(InvalidRefreshTokenException::new);
    UserAccount account = accounts.findById(session.userId()).orElseThrow(InvalidRefreshTokenException::new);
    if (!account.canSignIn(now)) {
      session.revoke(now);
      sessions.save(session);
      revocation.propagate(List.of(session.id()));
      throw new InvalidRefreshTokenException();
    }
    return credentialsIssuer.rotateSession(account, session, now);
  }
}
