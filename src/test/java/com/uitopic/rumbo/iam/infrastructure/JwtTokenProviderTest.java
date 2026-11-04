package com.uitopic.rumbo.iam.infrastructure;

import com.uitopic.rumbo.iam.application.ports.TokenProvider.AccessTokenClaims;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import com.uitopic.rumbo.iam.infrastructure.tokens.SecureRandomTokenGenerator;
import com.uitopic.rumbo.iam.infrastructure.tokens.jwt.JwtProperties;
import com.uitopic.rumbo.iam.infrastructure.tokens.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {
  private static final String SECRET = "unit-test-signing-key-with-enough-bytes";
  private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, "rumbo-api"), clock);

  @Test
  void anIssuedTokenCarriesTheClaims() {
    var claims = new AccessTokenClaims(UUID.randomUUID(), UserRole.CLINIC_ADMIN, UUID.randomUUID(), UUID.randomUUID());

    var issued = provider.issue(claims, Duration.ofMinutes(15));

    assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(provider.verify(issued.value()))
      .hasValueSatisfying(token -> assertThat(token.claims()).isEqualTo(claims));
  }

  @Test
  void aPatientTokenHasNoClinic() {
    var claims = new AccessTokenClaims(UUID.randomUUID(), UserRole.PATIENT, UUID.randomUUID(), null);

    assertThat(provider.verify(provider.issue(claims, Duration.ofMinutes(15)).value()))
      .hasValueSatisfying(token -> assertThat(token.claims().clinicId()).isNull());
  }

  @Test
  void expiredTamperedOrForeignTokensAreRejected() {
    var claims = new AccessTokenClaims(UUID.randomUUID(), UserRole.PATIENT, UUID.randomUUID(), null);
    String token = provider.issue(claims, Duration.ofMinutes(15)).value();
    var later = new JwtTokenProvider(new JwtProperties(SECRET, "rumbo-api"),
      Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
    var otherKey = new JwtTokenProvider(new JwtProperties(SECRET + "-other", "rumbo-api"), clock);
    var otherIssuer = new JwtTokenProvider(new JwtProperties(SECRET, "someone-else"), clock);

    assertThat(later.verify(token)).as("expired").isEmpty();
    assertThat(otherKey.verify(token)).as("signed with another key").isEmpty();
    assertThat(otherIssuer.verify(token)).as("another issuer").isEmpty();
    assertThat(provider.verify(token.substring(0, token.length() - 2) + "xx")).as("tampered").isEmpty();
    assertThat(provider.verify("not-a-jwt")).isEmpty();
    assertThat(provider.verify(null)).isEmpty();
  }

  @Test
  void aShortSecretStopsTheApplication() {
    assertThatThrownBy(() -> new JwtProperties("too-short", "rumbo-api")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new JwtProperties("", "rumbo-api")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void opaqueTokensAreUniqueAndStoredAsDigests() {
    var generator = new SecureRandomTokenGenerator();
    String token = generator.generate();

    assertThat(token).hasSize(43).doesNotContain("+", "/", "=");
    assertThat(generator.generate()).isNotEqualTo(token);
    assertThat(generator.digest(token)).hasSize(64).isEqualTo(generator.digest(token)).isNotEqualTo(token);
  }
}
