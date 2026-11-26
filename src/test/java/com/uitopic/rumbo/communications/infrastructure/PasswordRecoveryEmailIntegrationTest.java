package com.uitopic.rumbo.communications.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.iam.domain.model.events.PasswordResetRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real SMTP + PostgreSQL/Flyway + Redis. Tokens are obtained from the delivered email, not domain events. */
@SpringBootTest(properties = "rumbo.communications.recovery-mail.enabled=true")
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Testcontainers
class PasswordRecoveryEmailIntegrationTest {
  @Container static final GenericContainer<?> smtp = new GenericContainer<>("axllent/mailpit:v1.27.4")
      .withExposedPorts(1025, 8025);

  @DynamicPropertySource
  static void mailProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.mail.host", smtp::getHost);
    properties.add("spring.mail.port", () -> smtp.getMappedPort(1025));
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ApplicationEventPublisher events;
  @Autowired PlatformTransactionManager transactions;
  @Autowired HealthEndpoint health;

  @Test
  void enabledDeliveryChecksTheRealSmtpReceiver() {
    assertThat(health.healthForPath("mail").getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void adminAndVisitorRecoverUsingTheEmailAndTheLinkWorksOnce() throws Exception {
    for (String role : new String[]{"CLINIC_ADMIN", "PATIENT"}) {
      String email = UUID.randomUUID() + "@example.test";
      mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
          .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "Rumbo2026",
              "role", role, "fullName", "Ana Torres", "clinicName", "Clínica Central", "acceptedTerms", true))))
          .andExpect(status().isCreated());
      request(email);
      JsonNode delivered = emailTo(email);
      assertThat(delivered.path("Subject").asText()).contains("RUMBO");
      String text = delivered.path("Text").asText();
      assertThat(text).contains("una sola vez", "UTC", "http://localhost:4200/restablecer-clave?token=");
      var link = Pattern.compile("[?]token=([A-Za-z0-9_-]+)").matcher(text);
      assertThat(link.find()).isTrue();
      String token = link.group(1);
      String confirmation = json.writeValueAsString(java.util.Map.of("resetToken", token, "newPassword", "Rumbo2027"));
      mvc.perform(post("/api/v1/password-recovery/confirmations").contentType(MediaType.APPLICATION_JSON)
          .content(confirmation)).andExpect(status().isNoContent());
      mvc.perform(post("/api/v1/password-recovery/confirmations").contentType(MediaType.APPLICATION_JSON)
          .content(confirmation)).andExpect(status().isBadRequest());
      mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
          .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "Rumbo2027"))))
          .andExpect(status().isOk());
    }
  }

  @Test
  void unknownEmailHasNoDeliveryAndRolledBackEventsAreNotDelivered() throws Exception {
    int before = messages().size();
    request(UUID.randomUUID() + "@example.test");
    var transaction = new TransactionTemplate(transactions);
    transaction.executeWithoutResult(status -> {
      events.publishEvent(new PasswordResetRequestedEvent(UUID.randomUUID(), "rollback@example.test", "secret",
          Instant.now().plusSeconds(60), Instant.now()));
      status.setRollbackOnly();
    });
    assertThat(messages().size()).isEqualTo(before);
  }

  private void request(String email) throws Exception {
    var response = mvc.perform(post("/api/v1/password-recovery/requests").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(java.util.Map.of("email", email))))
        .andExpect(status().isAccepted()).andReturn().getResponse();
    assertThat(response.getContentAsString()).isEmpty();
  }

  private JsonNode emailTo(String email) throws Exception {
    for (JsonNode message : messages()) {
      for (JsonNode recipient : message.path("To")) {
        if (email.equals(recipient.path("Address").asText())) return get("message/" + message.path("ID").asText());
      }
    }
    throw new AssertionError("SMTP did not receive recovery instructions");
  }

  private JsonNode messages() throws Exception { return get("messages").path("messages"); }

  private JsonNode get(String path) throws Exception {
    URI uri = URI.create("http://" + smtp.getHost() + ":" + smtp.getMappedPort(8025) + "/api/v1/" + path);
    var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return json.readTree(response.body());
  }
}
