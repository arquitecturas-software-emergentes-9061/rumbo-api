package com.uitopic.rumbo.iam.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.iam.domain.model.events.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TS-26 and TS-01 against PostgreSQL in a container, through the whole HTTP and security stack. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@RecordApplicationEvents
class AuthenticationControllerIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired ApplicationEvents events;

  @Test
  void signUpStoresTheAccountWithAHashedPasswordAndPublishesTheEvent() throws Exception {
    String email = uniqueEmail();

    signUp(email, PASSWORD)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.email").value(email))
      .andExpect(jsonPath("$.role").value("CLINIC_ADMIN"))
      .andExpect(jsonPath("$.status").value("ACTIVE"))
      .andExpect(content().string(not(containsString(PASSWORD))));

    String hash = jdbc.queryForObject("select password_hash from identity_users where email = ?", String.class, email);
    assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
    assertThat(events.stream(UserRegisteredEvent.class)).singleElement().satisfies(event -> {
      assertThat(event.email()).isEqualTo(email);
      assertThat(event.fullName()).isEqualTo("Ana Torres");
      assertThat(event.clinicName()).isEqualTo("Clinica Central");
    });
  }

  @Test
  void aDuplicateEmailIsRejectedWithoutCreatingAnotherAccount() throws Exception {
    String email = uniqueEmail();
    signUp(email, PASSWORD).andExpect(status().isCreated());

    signUp(email.toUpperCase(), PASSWORD)
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

    assertThat(jdbc.queryForObject("select count(*) from identity_users where email = ?", Integer.class, email))
      .isEqualTo(1);
  }

  @Test
  void invalidFieldsAreReportedOneByOne() throws Exception {
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"not-an-email","password":"","role":"CLINIC_ADMIN","fullName":"","acceptedTerms":false}"""))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.errors.email").exists())
      .andExpect(jsonPath("$.errors.password").exists())
      .andExpect(jsonPath("$.errors.fullName").exists())
      .andExpect(jsonPath("$.errors.acceptedTerms").exists());
  }

  @Test
  void aWeakPasswordIsRejected() throws Exception {
    signUp(uniqueEmail(), "rumbo")
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
      .andExpect(jsonPath("$.errors.password").exists());
  }

  @Test
  void signInReturnsTokensThatAuthenticateLaterRequests() throws Exception {
    String email = uniqueEmail();
    signUp(email, PASSWORD).andExpect(status().isCreated());

    JsonNode credentials = json(signIn(email, PASSWORD)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.tokenType").value("Bearer"))
      .andExpect(jsonPath("$.expiresIn").value(900))
      .andExpect(jsonPath("$.refreshToken").exists()));

    mvc.perform(get("/api/v1/user-accounts/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.get("accessToken").asText()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(email));
    String digest = jdbc.queryForObject("select refresh_token_hash from identity_auth_sessions where user_id = ?",
      String.class, UUID.fromString(credentials.get("userId").asText()));
    assertThat(digest).hasSize(64).isNotEqualTo(credentials.get("refreshToken").asText());
  }

  @Test
  void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
    String email = uniqueEmail();
    signUp(email, PASSWORD).andExpect(status().isCreated());

    String wrongPassword = signIn(email, "Wrong2026").andExpect(status().isUnauthorized())
      .andReturn().getResponse().getContentAsString();
    String unknownEmail = signIn(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized())
      .andReturn().getResponse().getContentAsString();

    assertThat(mapper.readTree(wrongPassword).get("detail")).isEqualTo(mapper.readTree(unknownEmail).get("detail"));
    assertThat(mapper.readTree(wrongPassword).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  void theFifthFailedAttemptLocksTheAccountEvenForTheRightPassword() throws Exception {
    String email = uniqueEmail();
    signUp(email, PASSWORD).andExpect(status().isCreated());

    for (int attempt = 1; attempt < 5; attempt++) signIn(email, "Wrong2026").andExpect(status().isUnauthorized());
    signIn(email, "Wrong2026")
      .andExpect(status().isLocked())
      .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
      .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

    signIn(email, PASSWORD).andExpect(status().isLocked());
    assertThat(jdbc.queryForObject("select status from identity_users where email = ?", String.class, email))
      .isEqualTo("LOCKED");
  }

  @Test
  void protectedEndpointsRequireAValidToken() throws Exception {
    mvc.perform(get("/api/v1/user-accounts/me"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    mvc.perform(get("/api/v1/user-accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
      .andExpect(status().isUnauthorized());
  }

  private ResultActions signUp(String email, String password) throws Exception {
    return mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
      {"email":"%s","password":"%s","role":"CLINIC_ADMIN","fullName":"Ana Torres",
       "clinicName":"Clinica Central","acceptedTerms":true}""".formatted(email, password)));
  }

  private ResultActions signIn(String email, String password) throws Exception {
    return mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
      .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
  }

  private JsonNode json(ResultActions result) throws Exception {
    return mapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private static String uniqueEmail() {
    return "admin-" + UUID.randomUUID() + "@clinica.pe";
  }
}
