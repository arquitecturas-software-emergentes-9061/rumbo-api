package com.uitopic.rumbo.communications.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationIncidentReportedEvent;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import com.uitopic.rumbo.communications.application.commandhandlers.DeliverDueNotificationsCommandHandler;
import com.uitopic.rumbo.communications.application.ports.EmailNotificationSender;
import com.uitopic.rumbo.planning.domain.model.events.MapVersionPublishedEvent;
import com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-32, TS-35 and TS-36 through the real chain on the {@code postgres} profile: events after commit, the owner of
 * the site as the only recipient, the filtered center, and the delivery job against PostgreSQL with a fake mail
 * server. The scheduler is off, so each test runs the job when it needs it.
 */
@SpringBootTest(properties = "rumbo.communications.delivery.scheduler.enabled=false")
@ActiveProfiles("postgres")
@Import({TestcontainersConfiguration.class, OperationalNotificationsIntegrationTest.FakeMail.class})
@AutoConfigureMockMvc
class OperationalNotificationsIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";
  private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transactions;
  @Autowired DeliverDueNotificationsCommandHandler deliveries;
  @Autowired RecordingSender mail;

  private String ownerEmail;
  private String owner;
  private UUID siteId;

  @BeforeEach
  void ownerWithASite() throws Exception {
    mail.reset();
    ownerEmail = register("CLINIC_ADMIN");
    owner = tokenOf(ownerEmail);
    SubscriptionTestSeeds.activeEnterprise(jdbc, SubscriptionTestSeeds.ownerOf(owner));
    siteId = site(owner, "Sede Central");
  }

  @Test
  void anIncidentNotifiesOnlyTheOwnerOfTheSiteOnceAndIsDeliveredByEmail() throws Exception {
    String stranger = tokenOf(register("CLINIC_ADMIN"));
    NavigationIncidentReportedEvent incident = new NavigationIncidentReportedEvent(UUID.randomUUID(),
        UUID.randomUUID(), siteId, null, "pasillo-a", IncidentCategory.BLOCKED_PATH, null, null, NOW);
    publishInTransaction(incident);
    publishInTransaction(incident);

    String body = as(owner, get("/api/v1/notifications").param("type", "INCIDENT_REPORTED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].priority").value("HIGH"))
        .andExpect(jsonPath("$.items[0].siteId").value(siteId.toString()))
        .andExpect(jsonPath("$.items[0].channel").value("EMAIL"))
        .andExpect(jsonPath("$.items[0].title").value("Nuevo reporte en tu sede"))
        .andReturn().getResponse().getContentAsString();
    String id = mapper.readTree(body).get("items").get(0).get("id").asText();
    as(stranger, get("/api/v1/notifications").param("type", "INCIDENT_REPORTED"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    as(owner, get("/api/v1/notifications/{id}/delivery", id)).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.finalState").value(false));

    deliveries.handle();

    assertThat(mail.to(ownerEmail)).singleElement()
        .satisfies(sent -> assertThat(sent.subject()).isEqualTo("Rumbo — Nuevo reporte en tu sede"));
    as(owner, get("/api/v1/notifications/{id}/delivery", id)).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.finalState").value(true))
        .andExpect(jsonPath("$.attempts").value(1))
        .andExpect(jsonPath("$.nextAttemptAt").doesNotExist());
    deliveries.handle();
    assertThat(mail.to(ownerEmail)).hasSize(1);
  }

  @Test
  void aMailServerThatIsDownLeavesARetryVisibleInTheDeliveryState() throws Exception {
    mail.failTemporarily(true);
    publishInTransaction(new MapVersionPublishedEvent(siteId, UUID.randomUUID(), UUID.randomUUID(), NOW));

    deliveries.handle();

    String body = as(owner, get("/api/v1/notifications").param("type", "MAP_PUBLISHED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].priority").value("NORMAL"))
        .andReturn().getResponse().getContentAsString();
    String id = mapper.readTree(body).get("items").get(0).get("id").asText();
    as(owner, get("/api/v1/notifications/{id}/delivery", id)).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RETRY_PENDING"))
        .andExpect(jsonPath("$.attempts").value(1))
        .andExpect(jsonPath("$.nextAttemptAt").exists());
    assertThat(mail.to(ownerEmail)).isEmpty();
  }

  @Test
  void theCenterFiltersBySiteTypeAndPriorityAndSpeaksEnglishWhenAsked() throws Exception {
    UUID otherSite = site(owner, "Sede Norte");
    publishInTransaction(new NavigationIncidentReportedEvent(UUID.randomUUID(), null, siteId, null, null,
        IncidentCategory.SIGNAL_LOST, null, null, NOW));
    publishInTransaction(new MapVersionPublishedEvent(otherSite, UUID.randomUUID(), UUID.randomUUID(), NOW));

    as(owner, get("/api/v1/notifications").param("siteId", otherSite.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].type").value("MAP_PUBLISHED"));
    as(owner, get("/api/v1/notifications").param("priority", "HIGH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].type").value("INCIDENT_REPORTED"));
    as(owner, get("/api/v1/notifications").param("priority", "HIGH").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("New report at your site"));
    as(owner, get("/api/v1/notifications").param("priority", "URGENT")).andExpect(status().isBadRequest());
    as(owner, get("/api/v1/notifications")).andExpect(status().isOk())
        .andExpect(jsonPath("$.unread").value(3));
  }

  @Test
  void anEventOfASiteThatDoesNotExistNotifiesNobody() throws Exception {
    publishInTransaction(new NavigationIncidentReportedEvent(UUID.randomUUID(), null, UUID.randomUUID(), null, null,
        IncidentCategory.OTHER, null, null, NOW));

    as(owner, get("/api/v1/notifications").param("type", "INCIDENT_REPORTED"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  }

  private void publishInTransaction(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
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

  @TestConfiguration
  static class FakeMail {
    @Bean
    RecordingSender recordingSender() {
      return new RecordingSender();
    }
  }

  record Sent(String to, String subject, String body) { }

  /** Stands in for the SMTP server: records what it accepts, or fails like a server that is down. */
  static class RecordingSender implements EmailNotificationSender {
    final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    void reset() {
      sent.clear();
      failing = false;
    }

    List<Sent> to(String address) {
      return sent.stream().filter(mail -> mail.to().equals(address)).toList();
    }

    void failTemporarily(boolean failing) {
      this.failing = failing;
    }

    @Override
    public void send(String to, String subject, String body) {
      if (failing) throw new DeliveryFailure("server down", true, null);
      sent.add(new Sent(to, subject, body));
    }
  }
}
