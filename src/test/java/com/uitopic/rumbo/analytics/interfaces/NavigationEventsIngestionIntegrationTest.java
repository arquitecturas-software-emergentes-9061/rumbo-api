package com.uitopic.rumbo.analytics.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TS-30 scenario 1: telemetry from the device, with PostgreSQL and Redis in containers. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class NavigationEventsIngestionIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;

  private String token;
  private UUID siteId;
  private UUID sessionId;

  @BeforeEach
  void signedInPatient() throws Exception {
    String email = "patient-" + UUID.randomUUID() + "@mail.com";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"%s","password":"Rumbo2026","role":"PATIENT","fullName":"Luis Rojas","acceptedTerms":true}"""
        .formatted(email))).andExpect(status().isCreated());
    String body = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"Rumbo2026\"}".formatted(email)))
      .andReturn().getResponse().getContentAsString();
    token = mapper.readTree(body).get("accessToken").asText();
    siteId = UUID.randomUUID();
    sessionId = UUID.randomUUID();
  }

  @Test
  void aValidBatchIsAcceptedAndStoredWithoutTheUser() throws Exception {
    Instant start = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);

    send(batch(
      event("NAVIGATION_STARTED", start, Map.of("destinationNodeReference", "cardiologia-201")),
      event("DEVIATION_DETECTED", start.plusSeconds(60), Map.of("deviationMeters", 4.5)),
      event("NAVIGATION_FINISHED", start.plusSeconds(240), Map.of("result", "ARRIVED", "durationSeconds", 240))))
      .andExpect(status().isAccepted());

    assertThat(jdbc.queryForList("SELECT event_type FROM navigation_events WHERE navigation_session_id = ? "
      + "ORDER BY occurred_at", String.class, sessionId))
      .containsExactly("NAVIGATION_STARTED", "DEVIATION_DETECTED", "NAVIGATION_FINISHED");
    assertThat(jdbc.queryForObject("SELECT metadata ->> 'result' FROM navigation_events WHERE "
      + "navigation_session_id = ? AND event_type = 'NAVIGATION_FINISHED'", String.class, sessionId))
      .isEqualTo("ARRIVED");
    assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = "
      + "'navigation_events'", String.class)).noneMatch(column -> column.contains("user"));
  }

  @Test
  void personalDataInTheMetadataRejectsTheWholeBatch() throws Exception {
    send(batch(
      event("NAVIGATION_STARTED", Instant.now(), Map.of()),
      event("NAVIGATION_FINISHED", Instant.now(), Map.of("patientEmail", "luis@mail.com"))))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    assertThat(storedForSession()).isZero();
  }

  @Test
  void anUnknownEventTypeIsRejected() throws Exception {
    send(batch(event("TELEPORTED", Instant.now(), Map.of()))).andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void aBatchHasAtMostOneHundredEvents() throws Exception {
    String[] events = IntStream.range(0, 101).mapToObj(i -> event("DEVIATION_DETECTED", Instant.now(), Map.of()))
      .toArray(String[]::new);

    send(batch(events)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.events").exists());
    send(batch()).andExpect(status().isBadRequest());
  }

  @Test
  void anEventFromTheFutureIsRejected() throws Exception {
    send(batch(event("NAVIGATION_STARTED", Instant.now().plus(1, ChronoUnit.HOURS), Map.of())))
      .andExpect(status().isBadRequest());
  }

  @Test
  void recordingEventsInvalidatesTheMetricsOfTheSite() throws Exception {
    String generation = "analytics:metrics-generation:" + siteId;

    send(batch(event("NAVIGATION_STARTED", Instant.now(), Map.of()))).andExpect(status().isAccepted());

    assertThat(redis.opsForValue().get(generation)).isEqualTo("1");
  }

  @Test
  void anonymousTelemetryIsRejected() throws Exception {
    mvc.perform(post("/api/v1/analytics/navigation-events").contentType(MediaType.APPLICATION_JSON)
      .content(batch(event("NAVIGATION_STARTED", Instant.now(), Map.of())))).andExpect(status().isUnauthorized());
  }

  private ResultActions send(String body) throws Exception {
    return mvc.perform(post("/api/v1/analytics/navigation-events").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private Integer storedForSession() {
    return jdbc.queryForObject("SELECT count(*) FROM navigation_events WHERE navigation_session_id = ?",
      Integer.class, sessionId);
  }

  private static String batch(String... events) {
    return "{\"events\":[" + String.join(",", events) + "]}";
  }

  private String event(String type, Instant occurredAt, Map<String, Object> metadata) {
    try {
      return """
        {"navigationSessionId":"%s","siteId":"%s","floorId":"%s","eventType":"%s","nodeReference":"node-7",
         "occurredAt":"%s","metadata":%s}""".formatted(sessionId, siteId, UUID.randomUUID(), type, occurredAt,
        mapper.writeValueAsString(metadata));
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
