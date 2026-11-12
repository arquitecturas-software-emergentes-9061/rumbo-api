package com.uitopic.rumbo.profiles.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.iam.domain.model.events.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TS-33: the profile is created from {@code UserRegisteredEvent} only after the registration commits. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class ProfileInitializationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired ApplicationEventPublisher events;
  @Autowired PlatformTransactionManager transactions;

  @ParameterizedTest
  @ValueSource(strings = {"PATIENT", "CLINIC_ADMIN"})
  void aCommittedSignUpCreatesExactlyOneProfile(String role) throws Exception {
    String email = "profiles-" + UUID.randomUUID() + "@example.com";
    String body = mapper.writeValueAsString(Map.of("email", email, "password", "Rumbo2026", "role", role,
      "fullName", "Ana Torres", "clinicName", "Ignored clinic", "acceptedTerms", true));

    String response = mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();

    UUID userId = UUID.fromString(mapper.readTree(response).get("id").asText());
    assertThat(count(userId)).isEqualTo(1);
    Map<String, Object> profile = jdbc.queryForMap("select * from profiles_user_profiles where user_id = ?", userId);
    assertThat(profile.get("full_name")).isEqualTo("Ana Torres");
    assertThat(profile.get("role")).isEqualTo(role);
    assertThat(profile.get("phone_number")).isNull();
    assertThat(profile.get("image_url")).isNull();
    assertThat(profile.get("image_storage_key")).isNull();
  }

  @Test
  void replayingACommittedEventDoesNotDuplicateTheProfile() {
    UserRegisteredEvent event = event();
    TransactionTemplate transaction = new TransactionTemplate(transactions);

    transaction.executeWithoutResult(status -> events.publishEvent(event));
    transaction.executeWithoutResult(status -> events.publishEvent(event));

    assertThat(count(event.userId())).isEqualTo(1);
  }

  @Test
  void aRejectedSignUpCreatesNoProfile() throws Exception {
    long before = jdbc.queryForObject("select count(*) from profiles_user_profiles", Long.class);

    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"invalid","password":"","role":"PATIENT","fullName":"","acceptedTerms":false}
        """))
      .andExpect(status().isBadRequest());

    assertThat(jdbc.queryForObject("select count(*) from profiles_user_profiles", Long.class)).isEqualTo(before);
  }

  @Test
  void aRolledBackRegistrationCreatesNoProfile() {
    UserRegisteredEvent event = event();

    new TransactionTemplate(transactions).executeWithoutResult(status -> {
      events.publishEvent(event);
      status.setRollbackOnly();
    });

    assertThat(count(event.userId())).isZero();
  }

  @Test
  void anEventPublishedOutsideATransactionIsIgnored() {
    UserRegisteredEvent event = event();

    events.publishEvent(event);

    assertThat(count(event.userId())).isZero();
  }

  private long count(UUID userId) {
    return jdbc.queryForObject("select count(*) from profiles_user_profiles where user_id = ?", Long.class, userId);
  }

  private static UserRegisteredEvent event() {
    return new UserRegisteredEvent(UUID.randomUUID(), "ana@example.com", "PATIENT", "Ana Torres", null, Instant.now());
  }
}
