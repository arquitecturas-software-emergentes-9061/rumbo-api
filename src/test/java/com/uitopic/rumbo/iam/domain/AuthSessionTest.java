package com.uitopic.rumbo.iam.domain;

import com.uitopic.rumbo.iam.domain.model.aggregates.AuthSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthSessionTest {
  private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
  private static final Duration LIFETIME = Duration.ofDays(7);

  @Test
  void anOpenSessionIsActiveUntilItExpires() {
    AuthSession session = AuthSession.open(UUID.randomUUID(), UUID.randomUUID(), "digest", LIFETIME, NOW);

    assertThat(session.isActive(NOW.plus(Duration.ofDays(6)))).isTrue();
    assertThat(session.isActive(NOW.plus(LIFETIME))).isFalse();
  }

  @Test
  void rotatingReplacesTheTokenAndExtendsTheSession() {
    AuthSession session = AuthSession.open(UUID.randomUUID(), UUID.randomUUID(), "old", LIFETIME, NOW);
    Instant later = NOW.plus(Duration.ofDays(1));

    session.rotate("new", LIFETIME, later);

    assertThat(session.refreshTokenHash()).isEqualTo("new");
    assertThat(session.expiresAt()).isEqualTo(later.plus(LIFETIME));
  }

  @Test
  void aRevokedSessionCannotBeRotated() {
    AuthSession session = AuthSession.open(UUID.randomUUID(), UUID.randomUUID(), "old", LIFETIME, NOW);
    session.revoke(NOW);

    assertThatThrownBy(() -> session.rotate("new", LIFETIME, NOW)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void revokingTwiceKeepsTheFirstInstant() {
    AuthSession session = AuthSession.open(UUID.randomUUID(), UUID.randomUUID(), "digest", LIFETIME, NOW);

    session.revoke(NOW);
    session.revoke(NOW.plusSeconds(30));

    assertThat(session.revokedAt()).contains(NOW);
  }
}
