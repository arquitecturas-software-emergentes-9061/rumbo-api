package com.uitopic.rumbo.iam.infrastructure.cache.redis;

import com.uitopic.rumbo.iam.application.ports.PasswordResetTokenStore;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis adapter of {@link PasswordResetTokenStore}. Two keys per link, both expiring with it:
 * {@code iam:password-reset:token:<digest>} points to the account, and {@code iam:password-reset:user:<id>} points to
 * the account's current link so a new request can invalidate the previous one.
 */
@Component
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {
  static final String TOKEN_PREFIX = "iam:password-reset:token:";
  static final String USER_PREFIX = "iam:password-reset:user:";
  private final StringRedisTemplate redis;

  public RedisPasswordResetTokenStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void save(UUID userId, String tokenDigest, Duration ttl) {
    try {
      String previous = redis.opsForValue().getAndSet(USER_PREFIX + userId, tokenDigest);
      if (previous != null) redis.delete(TOKEN_PREFIX + previous);
      redis.expire(USER_PREFIX + userId, ttl);
      redis.opsForValue().set(TOKEN_PREFIX + tokenDigest, userId.toString(), ttl);
    } catch (DataAccessException failure) {
      throw new Unavailable(failure);
    }
  }

  @Override
  public Optional<UUID> consume(String tokenDigest) {
    try {
      String userId = redis.opsForValue().getAndDelete(TOKEN_PREFIX + tokenDigest);
      if (userId == null) return Optional.empty();
      redis.delete(USER_PREFIX + userId);
      return Optional.of(UUID.fromString(userId));
    } catch (DataAccessException failure) {
      throw new Unavailable(failure);
    }
  }
}
