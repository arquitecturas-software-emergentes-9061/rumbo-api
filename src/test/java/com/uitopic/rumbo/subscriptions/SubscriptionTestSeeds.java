package com.uitopic.rumbo.subscriptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class SubscriptionTestSeeds {
  private SubscriptionTestSeeds() {}

  public static UUID ownerOf(String token) {
    try {
      String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
      return UUID.fromString(new ObjectMapper().readTree(payload).get("sub").asText());
    } catch (java.io.IOException error) {
      throw new IllegalArgumentException("Invalid test token", error);
    }
  }

  public static void activeEnterprise(JdbcTemplate jdbc, UUID owner) {
    subscription(jdbc, owner, "ENTERPRISE", false);
  }

  /** Give synthetic map fixtures a real site owner and an unrestricted subscription. */
  public static void enterpriseSite(JdbcTemplate jdbc, UUID siteId) {
    var owners = jdbc.queryForList("SELECT owner_user_id FROM indoor_sites WHERE id = ?", UUID.class, siteId);
    if (!owners.isEmpty()) {
      activeEnterprise(jdbc, owners.getFirst());
      return;
    }
    UUID owner = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO identity_users (id, email, password_hash, role, status, created_at, updated_at)
        VALUES (?, ?, 'test-only', 'CLINIC_ADMIN', 'ACTIVE', now(), now())
        """, owner, "map-fixture-" + owner + "@example.test");
    activeEnterprise(jdbc, owner);
    jdbc.update("INSERT INTO indoor_sites (id, owner_user_id, name) VALUES (?, ?, 'Map fixture')", siteId, owner);
  }

  public static void subscription(JdbcTemplate jdbc, UUID owner, String plan, boolean expired) {
    jdbc.update("""
        INSERT INTO subscriptions_subscriptions
          (id, owner_user_id, plan_code, status, current_period_start, current_period_end, created_at, updated_at)
        VALUES (?, ?, ?, 'ACTIVE', now() - interval '2 days', now() + (? * interval '1 day'), now(), now())
        ON CONFLICT (owner_user_id) DO UPDATE SET plan_code = EXCLUDED.plan_code,
          status = EXCLUDED.status, current_period_start = EXCLUDED.current_period_start,
          current_period_end = EXCLUDED.current_period_end, updated_at = now()
        """, UUID.randomUUID(), owner, plan, expired ? -1 : 1);
  }
}
