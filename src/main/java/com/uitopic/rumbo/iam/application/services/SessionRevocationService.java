package com.uitopic.rumbo.iam.application.services;

import com.uitopic.rumbo.iam.application.ports.RevokedSessionCache;
import com.uitopic.rumbo.iam.domain.model.valueobjects.SessionPolicy;
import com.uitopic.rumbo.iam.domain.repositories.AuthSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Collection;
import java.util.UUID;

/**
 * Makes revoked sessions stop authenticating before their access tokens expire (TS-32).
 * <ul>
 *   <li>Every revocation is written to the cache, which the security filter checks on each request.</li>
 *   <li>If the cache cannot answer, the check falls back to the database, which is always the source of truth:
 *   slower, but a revoked session is never accepted.</li>
 *   <li>If the cache cannot be written, the revocation still holds in the database and the refresh token is dead;
 *   only access tokens already issued may live until they expire (at most the access-token lifetime).</li>
 * </ul>
 */
@Component
public class SessionRevocationService {
  private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);
  private final RevokedSessionCache cache;
  private final AuthSessionRepository sessions;
  private final SessionPolicy sessionPolicy;
  private final Clock clock;

  public SessionRevocationService(RevokedSessionCache cache, AuthSessionRepository sessions,
                                  SessionPolicy sessionPolicy, Clock clock) {
    this.cache = cache;
    this.sessions = sessions;
    this.sessionPolicy = sessionPolicy;
    this.clock = clock;
  }

  /** Publishes sessions already revoked in the database to the cache. */
  public void propagate(Collection<UUID> revokedSessionIds) {
    for (UUID sessionId : revokedSessionIds) {
      try {
        cache.markRevoked(sessionId, sessionPolicy.accessTokenLifetime());
      } catch (RevokedSessionCache.Unavailable unavailable) {
        log.warn("Session {} revoked in the database but not cached; its access tokens expire on their own",
          sessionId, unavailable);
      }
    }
  }

  public boolean isRevoked(UUID sessionId) {
    try {
      return cache.isRevoked(sessionId);
    } catch (RevokedSessionCache.Unavailable unavailable) {
      log.warn("Revoked session cache unavailable, checking session {} in the database", sessionId);
      return sessions.findById(sessionId).map(session -> !session.isActive(clock.instant())).orElse(true);
    }
  }
}
