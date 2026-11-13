package com.uitopic.rumbo.analytics.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.analytics.application.ports.NavigationMetricsReader;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DestinationUsage;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TS-30 scenarios 2 and 3: the dashboard summary through the real chain (JWT, site ownership, cache in Redis) on the
 * {@code postgres} profile. The SQL aggregation belongs to WI-TS30-03, so the read model is replaced by a test double
 * that honours the {@link NavigationMetricsReader} contract.
 */
@SpringBootTest(properties = {"rumbo.planning.editing.enabled=true", "rumbo.planning.publication.enabled=true"})
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class NavigationSummaryIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";
  private static final String FROM = "2026-11-01T00:00:00Z";
  private static final String TO = "2026-11-08T00:00:00Z";
  private static final DateRange WEEK = new DateRange(Instant.parse(FROM), Instant.parse(TO));

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean NavigationMetricsReader reader;

  private String owner;
  private UUID siteId;

  @BeforeEach
  void ownerWithASite() throws Exception {
    owner = tokenOf(register("CLINIC_ADMIN"));
    com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.activeEnterprise(jdbc,
        com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.ownerOf(owner));
    siteId = UUID.fromString(mapper.readTree(mvc.perform(post("/api/v1/sites").header(HttpHeaders.AUTHORIZATION,
        bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sede Central\"}"))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());
  }

  @Test
  void theOwnerGetsTheIndicatorsOfItsSite() throws Exception {
    when(reader.summarize(siteId, WEEK)).thenReturn(new NavigationMetrics(40, 34, 0.8, 215.5, 0.6, 0.25,
      List.of(new DestinationUsage("cardiologia-201", 12))));

    as(owner, summary(FROM, TO)).andExpect(status().isOk())
      .andExpect(jsonPath("$.siteId").value(siteId.toString()))
      .andExpect(jsonPath("$.hasData").value(true))
      .andExpect(jsonPath("$.startedSessions").value(40))
      .andExpect(jsonPath("$.completionRate").value(0.8))
      .andExpect(jsonPath("$.topDestinations[0].destinationNodeReference").value("cardiologia-201"));
  }

  @Test
  void aPeriodWithoutSessionsAnswersWithAnEmptySummary() throws Exception {
    when(reader.summarize(siteId, WEEK)).thenReturn(NavigationMetrics.EMPTY);

    as(owner, summary(FROM, TO)).andExpect(status().isOk()).andExpect(jsonPath("$.hasData").value(false))
      .andExpect(jsonPath("$.topDestinations").isEmpty());
  }

  @Test
  void aPeriodThatStartsAfterItEndsIsRejected() throws Exception {
    as(owner, summary(TO, FROM)).andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
  }

  @Test
  void aMissingOrMalformedDateIsRejected() throws Exception {
    as(owner, get("/api/v1/analytics/navigation-summary").param("siteId", siteId.toString()).param("from", FROM))
      .andExpect(status().isBadRequest());
    as(owner, summary("yesterday", TO)).andExpect(status().isBadRequest());
  }

  @Test
  void aSummaryIsCachedUntilNewEventsArriveForTheSite() throws Exception {
    when(reader.summarize(siteId, WEEK)).thenReturn(NavigationMetrics.EMPTY);

    as(owner, summary(FROM, TO)).andExpect(status().isOk());
    as(owner, summary(FROM, TO)).andExpect(status().isOk());
    verify(reader, times(1)).summarize(eq(siteId), any());

    mvc.perform(post("/api/v1/analytics/navigation-events").header(HttpHeaders.AUTHORIZATION, bearer(owner))
      .contentType(MediaType.APPLICATION_JSON).content("""
        {"events":[{"navigationSessionId":"%s","siteId":"%s","eventType":"NAVIGATION_STARTED",
         "occurredAt":"%s"}]}""".formatted(UUID.randomUUID(), siteId, Instant.now())))
      .andExpect(status().isAccepted());
    as(owner, summary(FROM, TO)).andExpect(status().isOk());
    verify(reader, times(2)).summarize(eq(siteId), any());
  }

  @Test
  void onlyTheAdministratorOfTheSiteCanReadIt() throws Exception {
    mvc.perform(summary(FROM, TO)).andExpect(status().isUnauthorized());
    as(tokenOf(register("CLINIC_ADMIN")), summary(FROM, TO)).andExpect(status().isForbidden());
    as(tokenOf(register("PATIENT")), summary(FROM, TO)).andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder summary(String from, String to) {
    return get("/api/v1/analytics/navigation-summary").param("siteId", siteId.toString()).param("from", from)
      .param("to", to);
  }

  private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
  }

  private String register(String role) throws Exception {
    String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@clinica.pe";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"%s","password":"%s","role":"%s","fullName":"Ana Torres","clinicName":"Clinica Central",
         "acceptedTerms":true}""".formatted(email, PASSWORD, role)))
      .andExpect(status().isCreated());
    return email;
  }

  private String tokenOf(String email) throws Exception {
    String body = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return mapper.readTree(body).get("accessToken").asText();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
