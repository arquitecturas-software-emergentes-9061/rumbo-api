package com.uitopic.rumbo.analytics.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.infrastructure.persistence.jdbc.JdbcNavigationMetricsReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PR-241 / TS-30: event persistence, real JDBC metrics and inverted date ranges through the authenticated API.
 * Each test owns a fresh site so PostgreSQL events and shared Redis summaries remain isolated.
 */
@SpringBootTest(properties = {"rumbo.planning.editing.enabled=true", "rumbo.planning.publication.enabled=true"})
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class NavigationDataIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";
  private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-11-08T00:00:00Z");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @MockitoSpyBean JdbcNavigationMetricsReader reader;

  private String owner;
  private UUID siteId;

  @BeforeEach
  void ownerWithASite() throws Exception {
    String email = "clinic-admin-" + UUID.randomUUID() + "@clinica.pe";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"%s","password":"%s","role":"CLINIC_ADMIN","fullName":"Ana Torres",
         "clinicName":"Clinica Central","acceptedTerms":true}""".formatted(email, PASSWORD)))
      .andExpect(status().isCreated());
    String body = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    owner = mapper.readTree(body).get("accessToken").asText();
    com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.activeEnterprise(jdbc,
        com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.ownerOf(owner)); // sites require an active plan (PR-93)
    siteId = UUID.fromString(mapper.readTree(mvc.perform(post("/api/v1/sites")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Sede Central\"}"))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());
  }

  @Test
  void validStartedAndFinishedEventsAreAcceptedAndStored() throws Exception {
    UUID sessionId = UUID.randomUUID();
    Map<String, Object> startMetadata = Map.of("destinationNodeReference", "cardiologia-201");
    Map<String, Object> finishMetadata = Map.of("result", "ARRIVED", "durationSeconds", 240);

    send(event(sessionId, "NAVIGATION_STARTED", FROM, startMetadata),
      event(sessionId, "NAVIGATION_FINISHED", FROM.plusSeconds(240), finishMetadata))
      .andExpect(status().isAccepted());

    var stored = jdbc.queryForList("SELECT site_id, event_type, occurred_at, metadata::text AS metadata "
      + "FROM navigation_events WHERE navigation_session_id = ? ORDER BY occurred_at", sessionId);
    assertThat(stored).hasSize(2);
    assertThat(stored).allSatisfy(row -> assertThat(row.get("site_id")).isEqualTo(siteId));
    assertThat(stored).extracting(row -> row.get("event_type"))
      .containsExactly("NAVIGATION_STARTED", "NAVIGATION_FINISHED");
    assertThat(jdbc.query("SELECT occurred_at FROM navigation_events WHERE navigation_session_id = ? "
      + "ORDER BY occurred_at", (rs, row) -> rs.getTimestamp("occurred_at").toInstant(), sessionId))
      .containsExactly(FROM, FROM.plusSeconds(240));
    assertThat(mapper.readTree((String) stored.get(0).get("metadata")))
      .isEqualTo(mapper.valueToTree(startMetadata));
    assertThat(mapper.readTree((String) stored.get(1).get("metadata")))
      .isEqualTo(mapper.valueToTree(finishMetadata));
  }

  @Test
  void theRealReaderSummarizesTheSiteAndExcludesEventsAtTheUpperBound() throws Exception {
    UUID arrived = UUID.randomUUID();
    UUID unfinished = UUID.randomUUID();
    UUID atUpperBound = UUID.randomUUID();
    send(event(arrived, "NAVIGATION_STARTED", FROM, Map.of("destinationNodeReference", "cardiologia-201")),
      event(arrived, "NAVIGATION_FINISHED", FROM.plusSeconds(240),
        Map.of("result", "ARRIVED", "durationSeconds", 240)),
      event(unfinished, "NAVIGATION_STARTED", FROM.plusSeconds(60),
        Map.of("destinationNodeReference", "cardiologia-201")),
      event(unfinished, "NAVIGATION_FINISHED", TO, Map.of("result", "ARRIVED", "durationSeconds", 600)),
      event(atUpperBound, "NAVIGATION_STARTED", TO, Map.of("destinationNodeReference", "excluded-destination")))
      .andExpect(status().isAccepted());

    summary(FROM, TO).andExpect(status().isOk())
      .andExpect(jsonPath("$.siteId").value(siteId.toString()))
      .andExpect(jsonPath("$.from").value(FROM.toString()))
      .andExpect(jsonPath("$.to").value(TO.toString()))
      .andExpect(jsonPath("$.hasData").value(true))
      .andExpect(jsonPath("$.startedSessions").value(2))
      .andExpect(jsonPath("$.finishedSessions").value(1))
      .andExpect(jsonPath("$.completionRate").value(0.5))
      .andExpect(jsonPath("$.averageDurationSeconds").value(240.0))
      .andExpect(jsonPath("$.topDestinations.length()").value(1))
      .andExpect(jsonPath("$.topDestinations[0].destinationNodeReference").value("cardiologia-201"))
      .andExpect(jsonPath("$.topDestinations[0].sessionCount").value(2));

    verify(reader).summarize(siteId, new DateRange(FROM, TO));
  }

  @Test
  void anInvertedIntervalIsRejectedWithoutReadingMetricsOrWritingDataOrCache() throws Exception {
    String metricsKeys = "analytics:metrics:" + siteId + ":*";
    String generationKey = "analytics:metrics-generation:" + siteId;
    assertThat(redis.keys(metricsKeys)).isEmpty();
    assertThat(redis.opsForValue().get(generationKey)).isNull();
    assertThat(storedForSite()).isZero();

    summary(TO, FROM).andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

    verifyNoInteractions(reader);
    assertThat(redis.keys(metricsKeys)).isEmpty();
    assertThat(redis.opsForValue().get(generationKey)).isNull();
    assertThat(storedForSite()).isZero();
  }

  private ResultActions summary(Instant from, Instant to) throws Exception {
    return mvc.perform(get("/api/v1/analytics/navigation-summary")
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner).param("siteId", siteId.toString())
      .param("from", from.toString()).param("to", to.toString()));
  }

  @SafeVarargs
  private final ResultActions send(Map<String, Object>... events) throws Exception {
    return mvc.perform(post("/api/v1/analytics/navigation-events")
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(Map.of("events", events))));
  }

  private Map<String, Object> event(UUID sessionId, String type, Instant occurredAt, Map<String, Object> metadata) {
    return Map.of("navigationSessionId", sessionId.toString(), "siteId", siteId.toString(), "eventType", type,
      "occurredAt", occurredAt.toString(), "metadata", metadata);
  }

  private Integer storedForSite() {
    return jdbc.queryForObject("SELECT count(*) FROM navigation_events WHERE site_id = ?", Integer.class, siteId);
  }
}
