package com.uitopic.rumbo.iam.application;

import com.uitopic.rumbo.iam.application.ports.RevokedSessionCache;
import com.uitopic.rumbo.iam.application.services.SessionRevocationService;
import com.uitopic.rumbo.iam.domain.model.aggregates.AuthSession;
import com.uitopic.rumbo.iam.domain.model.valueobjects.SessionPolicy;
import com.uitopic.rumbo.iam.domain.repositories.AuthSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Controlled degradation when Redis is down: the database decides, and a revoked session is never accepted. */
class SessionRevocationServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
  private static final SessionPolicy POLICY = new SessionPolicy(Duration.ofMinutes(15), Duration.ofDays(7));
  private final RevokedSessionCache cache = mock(RevokedSessionCache.class);
  private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
  private final SessionRevocationService service = new SessionRevocationService(cache, sessions, POLICY,
    Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void theCacheAnswersWhileItIsAvailable() {
    UUID sessionId = UUID.randomUUID();
    when(cache.isRevoked(sessionId)).thenReturn(true);

    assertThat(service.isRevoked(sessionId)).isTrue();
    verifyNoInteractions(sessions);
  }

  @Test
  void withoutTheCacheTheDatabaseDecides() {
    AuthSession active = AuthSession.open(UUID.randomUUID(), UUID.randomUUID(), "a", Duration.ofDays(7), NOW);
    AuthSession revoked = AuthSession.open(UUID.randomUUID(), UUID.randomUUID(), "b", Duration.ofDays(7), NOW);
    revoked.revoke(NOW);
    when(cache.isRevoked(any())).thenThrow(new RevokedSessionCache.Unavailable(new RuntimeException("down")));
    when(sessions.findById(active.id())).thenReturn(Optional.of(active));
    when(sessions.findById(revoked.id())).thenReturn(Optional.of(revoked));

    assertThat(service.isRevoked(active.id())).isFalse();
    assertThat(service.isRevoked(revoked.id())).isTrue();
    assertThat(service.isRevoked(UUID.randomUUID())).as("unknown session").isTrue();
  }

  @Test
  void aFailedCacheWriteDoesNotUndoTheRevocation() {
    UUID sessionId = UUID.randomUUID();
    doThrow(new RevokedSessionCache.Unavailable(new RuntimeException("down")))
      .when(cache).markRevoked(eq(sessionId), any());

    assertThatCode(() -> service.propagate(List.of(sessionId))).doesNotThrowAnyException();
    verify(cache).markRevoked(sessionId, Duration.ofMinutes(15));
  }
}
