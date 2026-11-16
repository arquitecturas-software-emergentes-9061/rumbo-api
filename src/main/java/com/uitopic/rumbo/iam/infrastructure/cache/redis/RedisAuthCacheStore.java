package com.uitopic.rumbo.iam.infrastructure.cache.redis;

import com.uitopic.rumbo.iam.application.ports.RevokedSessionCache;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** Redis adapter of {@link RevokedSessionCache}: one key per revoked session that expires with its access tokens. */
@Component
public class RedisAuthCacheStore implements RevokedSessionCache {
  static final String REVOKED_SESSION_PREFIX = "iam:revoked-session:";
  private final StringRedisTemplate redis;

  public RedisAuthCacheStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void markRevoked(UUID sessionId, Duration ttl) {
    try {
      redis.opsForValue().set(REVOKED_SESSION_PREFIX + sessionId, "1", ttl);
    } catch (DataAccessException failure) {
      throw new Unavailable(failure);
    }
  }

  @Override
  public boolean isRevoked(UUID sessionId) {
    try {
      return Boolean.TRUE.equals(redis.hasKey(REVOKED_SESSION_PREFIX + sessionId));
    } catch (DataAccessException failure) {
      throw new Unavailable(failure);
    }
  }
}
