package com.uitopic.rumbo.analytics.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationIncidentReportedEvent;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationRatedEvent;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TS-31 and US-25 through the real chain (JWT, site ownership, events after commit) on the {@code postgres} profile.
 * The registration of ratings and reports is replaced by publishing its events, the contract it has to honour.
 */
@SpringBootTest(properties = {"rumbo.planning.editing.enabled=true", "rumbo.planning.publication.enabled=true"})
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class NavigationFeedbackInsightsIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";
  private static final String FROM = "2026-11-01T05:00:00Z";
  private static final String TO = "2026-11-08T05:00:00Z";
  private static final Instant DURING = Instant.parse("2026-11-03T15:00:00Z");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transactions;

  private String owner;
  private UUID siteId;
  private UUID otherSiteId;

  @BeforeEach
  void ownerWithTwoSites() throws Exception {
    owner = tokenOf(register("CLINIC_ADMIN"));
    SubscriptionTestSeeds.activeEnterprise(jdbc, SubscriptionTestSeeds.ownerOf(owner));
    siteId = site(owner, "Sede Central");
    otherSiteId = site(owner, "Sede Norte");
  }

  @Test
  void feedbackPublishedAfterCommitReachesTheDashboardOnce() throws Exception {
    UUID session = UUID.randomUUID();
    NavigationRatedEvent rating = new NavigationRatedEvent(UUID.randomUUID(), session, siteId, "cardiologia-201", 4,
      DURING);
    publishInTransaction(rating);
    publishInTransaction(rating);
    publishInTransaction(new NavigationIncidentReportedEvent(UUID.randomUUID(), session, siteId, null, null,
      IncidentCategory.SIGNAL_LOST, null, null, DURING.plusSeconds(5)));

    as(owner, period("/api/v1/analytics/feedback-summary", siteId)).andExpect(status().isOk())
      .andExpect(jsonPath("$.ratings").value(1))
      .andExpect(jsonPath("$.averageScore").value(4.0))
      .andExpect(jsonPath("$.incidents").value(1))
      .andExpect(jsonPath("$.incidentsWithoutPosition").value(1))
      .andExpect(jsonPath("$.incidentsByCategory[0].category").value("SIGNAL_LOST"));
    as(owner, get("/api/v1/analytics/navigation-sessions/{id}/feedback", session).param("siteId", siteId.toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.rating.score").value(4))
      .andExpect(jsonPath("$.incidents.length()").value(1));
  }

  @Test
  void aRolledBackRegistrationLeavesNoFact() throws Exception {
    try {
      transactions.executeWithoutResult(status -> {
        events.publishEvent(new NavigationRatedEvent(UUID.randomUUID(), UUID.randomUUID(), siteId, null, 2, DURING));
        throw new IllegalStateException("registration failed");
      });
    } catch (IllegalStateException expected) {
      // the registration did not commit
    }

    as(owner, period("/api/v1/analytics/feedback-summary", siteId)).andExpect(status().isOk())
      .andExpect(jsonPath("$.ratings").value(0));
  }

  @Test
  void theSessionOfAnotherSiteIsNotFound() throws Exception {
    UUID session = UUID.randomUUID();
    publishInTransaction(new NavigationRatedEvent(UUID.randomUUID(), session, siteId, null, 5, DURING));

    as(owner, get("/api/v1/analytics/navigation-sessions/{id}/feedback", session)
        .param("siteId", otherSiteId.toString()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("SESSION_FEEDBACK_NOT_FOUND"));
  }

  @Test
  void incidentReportsArePagedAndValidated() throws Exception {
    publishInTransaction(new NavigationIncidentReportedEvent(UUID.randomUUID(), null, siteId, UUID.randomUUID(),
      "pasillo-a", IncidentCategory.BLOCKED_PATH, 3.0, 4.0, DURING));

    as(owner, period("/api/v1/analytics/incident-reports", siteId).param("category", "BLOCKED_PATH"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(1))
      .andExpect(jsonPath("$.items[0].nodeReference").value("pasillo-a"))
      .andExpect(jsonPath("$.items[0].x").value(3.0));
    as(owner, period("/api/v1/analytics/incident-reports", siteId).param("size", "0"))
      .andExpect(status().isBadRequest());
    as(owner, period("/api/v1/analytics/incident-reports", siteId).param("category", "FIRE"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void aTrendHasEveryDayAndRejectsPeriodsTooLongForItsGranularity() throws Exception {
    as(owner, period("/api/v1/analytics/navigation-trends", siteId)).andExpect(status().isOk())
      .andExpect(jsonPath("$.granularity").value("DAY"))
      .andExpect(jsonPath("$.zone").value("America/Lima"))
      .andExpect(jsonPath("$.points.length()").value(7))
      .andExpect(jsonPath("$.points[0].periodStart").value("2026-11-01T05:00:00Z"));
    as(owner, get("/api/v1/analytics/navigation-trends").param("siteId", siteId.toString())
        .param("from", "2026-01-01T05:00:00Z").param("to", "2026-11-01T05:00:00Z"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    as(owner, period("/api/v1/analytics/navigation-trends", siteId).param("zone", "Mars/Olympus"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void incidentZonesValidateTheCellSize() throws Exception {
    as(owner, period("/api/v1/analytics/incident-zones", siteId)).andExpect(status().isOk())
      .andExpect(jsonPath("$.cellSize").value(2.0))
      .andExpect(jsonPath("$.zones").isEmpty());
    as(owner, period("/api/v1/analytics/incident-zones", siteId).param("cellSize", "0"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void theOverviewListsOnlyTheSitesOfTheCaller() throws Exception {
    publishInTransaction(new NavigationRatedEvent(UUID.randomUUID(), UUID.randomUUID(), siteId, null, 5, DURING));
    String stranger = tokenOf(register("CLINIC_ADMIN"));

    as(owner, overview()).andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[0].siteName").value("Sede Central"))
      .andExpect(jsonPath("$[0].ratings").value(1))
      .andExpect(jsonPath("$[1].siteName").value("Sede Norte"))
      .andExpect(jsonPath("$[1].ratings").value(0));
    as(stranger, overview()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void onlyTheAdministratorOfTheSiteReadsItsReports() throws Exception {
    String stranger = tokenOf(register("CLINIC_ADMIN"));
    String patient = tokenOf(register("PATIENT"));

    for (String path : new String[] {"/api/v1/analytics/feedback-summary", "/api/v1/analytics/incident-reports",
        "/api/v1/analytics/navigation-trends", "/api/v1/analytics/incident-zones"}) {
      mvc.perform(period(path, siteId)).andExpect(status().isUnauthorized());
      as(stranger, period(path, siteId)).andExpect(status().isForbidden());
      as(patient, period(path, siteId)).andExpect(status().isForbidden());
    }
    as(patient, overview()).andExpect(status().isForbidden());
  }

  private void publishInTransaction(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  private MockHttpServletRequestBuilder period(String path, UUID site) {
    return get(path).param("siteId", site.toString()).param("from", FROM).param("to", TO);
  }

  private MockHttpServletRequestBuilder overview() {
    return get("/api/v1/analytics/sites-overview").param("from", FROM).param("to", TO);
  }

  private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private UUID site(String token, String name) throws Exception {
    String body = mvc.perform(post("/api/v1/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return UUID.fromString(mapper.readTree(body).get("id").asText());
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
}
