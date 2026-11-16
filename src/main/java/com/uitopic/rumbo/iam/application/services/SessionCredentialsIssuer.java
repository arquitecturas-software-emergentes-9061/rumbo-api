package com.uitopic.rumbo.iam.application.services;

import com.uitopic.rumbo.iam.application.ports.SecureTokenGenerator;
import com.uitopic.rumbo.iam.application.ports.TokenProvider;
import com.uitopic.rumbo.iam.application.ports.TokenProvider.AccessTokenClaims;
import com.uitopic.rumbo.iam.application.results.AuthenticatedUser;
import com.uitopic.rumbo.iam.domain.model.aggregates.AuthSession;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.valueobjects.SessionPolicy;
import com.uitopic.rumbo.iam.domain.repositories.AuthSessionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Opens and rotates sessions and issues their credentials, so sign-in and refresh follow exactly the same rules.
 * It is not a use case: the command handlers call it inside their own transaction.
 */
@Component
public class SessionCredentialsIssuer {
  private final AuthSessionRepository sessions;
  private final TokenProvider tokenProvider;
  private final SecureTokenGenerator tokenGenerator;
  private final SessionPolicy sessionPolicy;

  public SessionCredentialsIssuer(AuthSessionRepository sessions, TokenProvider tokenProvider,
                                  SecureTokenGenerator tokenGenerator, SessionPolicy sessionPolicy) {
    this.sessions = sessions;
    this.tokenProvider = tokenProvider;
    this.tokenGenerator = tokenGenerator;
    this.sessionPolicy = sessionPolicy;
  }

  /** Opens a new session for an account that already passed its sign-in checks. */
  public AuthenticatedUser openSession(UserAccount account, Instant now) {
    String refreshToken = tokenGenerator.generate();
    AuthSession session = AuthSession.open(UUID.randomUUID(), account.id(), tokenGenerator.digest(refreshToken),
      sessionPolicy.refreshTokenLifetime(), now);
    return credentials(account, sessions.save(session), refreshToken);
  }

  /** Rotates an active session: the previous refresh token stops working and new credentials are issued. */
  public AuthenticatedUser rotateSession(UserAccount account, AuthSession session, Instant now) {
    String refreshToken = tokenGenerator.generate();
    session.rotate(tokenGenerator.digest(refreshToken), sessionPolicy.refreshTokenLifetime(), now);
    return credentials(account, sessions.save(session), refreshToken);
  }

  private AuthenticatedUser credentials(UserAccount account, AuthSession session, String refreshToken) {
    AccessTokenClaims claims = new AccessTokenClaims(account.id(), account.role(), session.id(),
      account.clinicId().orElse(null));
    TokenProvider.IssuedAccessToken accessToken = tokenProvider.issue(claims, sessionPolicy.accessTokenLifetime());
    return new AuthenticatedUser(account.id(), account.role(), claims.clinicId(), session.id(), accessToken.value(),
      accessToken.expiresAt(), refreshToken, session.expiresAt());
  }
}
