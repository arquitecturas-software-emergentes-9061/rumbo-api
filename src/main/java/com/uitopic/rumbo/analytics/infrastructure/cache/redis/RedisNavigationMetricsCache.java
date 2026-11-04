package com.uitopic.rumbo.analytics.infrastructure.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.analytics.application.ports.NavigationMetricsCache;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis adapter of {@link NavigationMetricsCache}. Invalidating a site does not search its keys: every site has a
 * generation counter ({@code analytics:metrics-generation:<site>}) that is part of each key, so incrementing it
 * makes all older entries unreachable at once, and they expire on their own.
 */
@Component
public class RedisNavigationMetricsCache implements NavigationMetricsCache {
  static final String GENERATION_PREFIX = "analytics:metrics-generation:";
  static final String METRICS_PREFIX = "analytics:metrics:";
  private static final Logger log = LoggerFactory.getLogger(RedisNavigationMetricsCache.class);
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final Duration ttl;

  public RedisNavigationMetricsCache(StringRedisTemplate redis, ObjectMapper mapper,
                                     @Value("${rumbo.analytics.metrics-cache-ttl:5m}") Duration ttl) {
    this.redis = redis;
    this.mapper = mapper;
    this.ttl = ttl;
  }

  @Override
  public Optional<NavigationMetrics> get(UUID siteId, DateRange period) {
    try {
      String cached = redis.opsForValue().get(key(siteId, period));
      return cached == null ? Optional.empty() : Optional.of(mapper.readValue(cached, NavigationMetrics.class));
    } catch (DataAccessException | JsonProcessingException failure) {
      log.warn("Metrics cache unavailable for site {}, calculating again", siteId, failure);
      return Optional.empty();
    }
  }

  @Override
  public void put(UUID siteId, DateRange period, NavigationMetrics metrics) {
    try {
      redis.opsForValue().set(key(siteId, period), mapper.writeValueAsString(metrics), ttl);
    } catch (DataAccessException | JsonProcessingException failure) {
      log.warn("Metrics of site {} not cached", siteId, failure);
    }
  }

  @Override
  public void invalidate(Collection<UUID> siteIds) {
    for (UUID siteId : siteIds) {
      try {
        redis.opsForValue().increment(GENERATION_PREFIX + siteId);
      } catch (DataAccessException failure) {
        log.warn("Metrics cache of site {} not invalidated; it refreshes within {}", siteId, ttl, failure);
      }
    }
  }

  private String key(UUID siteId, DateRange period) {
    String generation = redis.opsForValue().get(GENERATION_PREFIX + siteId);
    return METRICS_PREFIX + siteId + ":" + (generation == null ? "0" : generation) + ":"
      + period.from().toEpochMilli() + "-" + period.to().toEpochMilli();
  }
}
