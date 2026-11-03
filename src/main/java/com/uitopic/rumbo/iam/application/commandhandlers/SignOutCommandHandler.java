package com.uitopic.rumbo.iam.application.commandhandlers;

import com.uitopic.rumbo.iam.application.services.SessionRevocationService;
import com.uitopic.rumbo.iam.domain.model.commands.SignOutCommand;
import com.uitopic.rumbo.iam.domain.repositories.AuthSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Ends a session (TS-32): its refresh token stops working and its access tokens are rejected from the next request.
 * Idempotent, and a session of another account is ignored without saying whether it exists.
 */
@Service
public class SignOutCommandHandler {
  private final AuthSessionRepository sessions;
  private final SessionRevocationService revocation;
  private final Clock clock;

  public SignOutCommandHandler(AuthSessionRepository sessions, SessionRevocationService revocation, Clock clock) {
    this.sessions = sessions;
    this.revocation = revocation;
    this.clock = clock;
  }

  @Transactional
  public void handle(SignOutCommand command) {
    Instant now = clock.instant();
    sessions.findById(command.sessionId())
      .filter(session -> session.userId().equals(command.userId()))
      .ifPresent(session -> {
        session.revoke(now);
        sessions.save(session);
        revocation.propagate(List.of(session.id()));
      });
  }
}
