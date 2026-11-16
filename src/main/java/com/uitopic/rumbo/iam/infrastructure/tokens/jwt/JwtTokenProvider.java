package com.uitopic.rumbo.iam.infrastructure.tokens.jwt;

import com.uitopic.rumbo.iam.application.ports.TokenProvider;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * JJWT adapter of {@link TokenProvider}: HS256 access tokens with claims {@code sub} (user), {@code role},
 * {@code sid} (session) and {@code clinicId}. Anything that fails verification is treated as no token at all.
 */
@Component
public class JwtTokenProvider implements TokenProvider {
  static final String ROLE = "role";
  static final String SESSION_ID = "sid";
  static final String CLINIC_ID = "clinicId";

  private final SecretKey key;
  private final String issuer;
  private final Clock clock;
  private final JwtParser parser;

  public JwtTokenProvider(JwtProperties properties, Clock clock) {
    this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    this.issuer = properties.issuer();
    this.clock = clock;
    this.parser = Jwts.parser().verifyWith(key).requireIssuer(issuer)
      .clock(() -> Date.from(clock.instant())).build();
  }

  @Override
  public IssuedAccessToken issue(AccessTokenClaims claims, Duration lifetime) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(lifetime);
    var builder = Jwts.builder()
      .id(UUID.randomUUID().toString())
      .issuer(issuer)
      .subject(claims.userId().toString())
      .issuedAt(Date.from(now))
      .expiration(Date.from(expiresAt))
      .claim(ROLE, claims.role().name())
      .claim(SESSION_ID, claims.sessionId().toString());
    if (claims.clinicId() != null) builder.claim(CLINIC_ID, claims.clinicId().toString());
    return new IssuedAccessToken(builder.signWith(key, Jwts.SIG.HS256).compact(), expiresAt);
  }

  @Override
  public Optional<VerifiedAccessToken> verify(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    try {
      Claims payload = parser.parseSignedClaims(token).getPayload();
      String clinicId = payload.get(CLINIC_ID, String.class);
      AccessTokenClaims claims = new AccessTokenClaims(UUID.fromString(payload.getSubject()),
        UserRole.valueOf(payload.get(ROLE, String.class)), UUID.fromString(payload.get(SESSION_ID, String.class)),
        clinicId == null ? null : UUID.fromString(clinicId));
      return Optional.of(new VerifiedAccessToken(claims, payload.getExpiration().toInstant()));
    } catch (JwtException | IllegalArgumentException | NullPointerException untrusted) {
      return Optional.empty();
    }
  }
}
