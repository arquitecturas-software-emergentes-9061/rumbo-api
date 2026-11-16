package com.uitopic.rumbo.iam.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TS-32: rotation, sign-out and the session list, with PostgreSQL and Redis in containers. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class SessionLifecycleIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired StringRedisTemplate redis;

  @Test
  void aRefreshTokenWorksOnlyOnce() throws Exception {
    JsonNode signIn = signIn(registeredEmail());
    String first = signIn.get("refreshToken").asText();

    JsonNode rotated = json(refresh(first).andExpect(status().isOk()));
    String second = rotated.get("refreshToken").asText();

    assertThat(second).isNotEqualTo(first);
    assertThat(rotated.get("accessToken").asText()).isNotEqualTo(signIn.get("accessToken").asText());
    refresh(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    refresh(second).andExpect(status().isOk());
  }

  @Test
  void signingOutStopsBothTokensAtOnce() throws Exception {
    JsonNode credentials = signIn(registeredEmail());
    String accessToken = credentials.get("accessToken").asText();
    UUID sessionId = currentSessionId(accessToken);

    mvc.perform(post("/api/v1/auth/sign-out").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
      .andExpect(status().isNoContent());

    me(accessToken).andExpect(status().isUnauthorized());
    refresh(credentials.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    Long ttl = redis.getExpire("iam:revoked-session:" + sessionId);
    assertThat(ttl).isBetween(1L, 900L);
  }

  @Test
  void signOutRequiresAToken() throws Exception {
    mvc.perform(post("/api/v1/auth/sign-out")).andExpect(status().isUnauthorized());
  }

  @Test
  void aUserCanEndTheSessionOfAnotherDevice() throws Exception {
    String email = registeredEmail();
    String phone = signIn(email).get("accessToken").asText();
    String laptop = signIn(email).get("accessToken").asText();
    UUID phoneSession = currentSessionId(phone);

    JsonNode sessions = json(mvc.perform(get("/api/v1/user-accounts/me/sessions")
      .header(HttpHeaders.AUTHORIZATION, bearer(laptop))).andExpect(status().isOk()));
    assertThat(sessions).hasSize(2);
    assertThat(sessions).filteredOn(session -> session.get("current").asBoolean()).hasSize(1);

    mvc.perform(delete("/api/v1/user-accounts/me/sessions/{id}", phoneSession)
        .header(HttpHeaders.AUTHORIZATION, bearer(laptop)))
      .andExpect(status().isNoContent());

    me(phone).andExpect(status().isUnauthorized());
    me(laptop).andExpect(status().isOk());
  }

  @Test
  void theSessionOfAnotherAccountCannotBeEnded() throws Exception {
    String victim = signIn(registeredEmail()).get("accessToken").asText();
    String attacker = signIn(registeredEmail()).get("accessToken").asText();

    mvc.perform(delete("/api/v1/user-accounts/me/sessions/{id}", currentSessionId(victim))
        .header(HttpHeaders.AUTHORIZATION, bearer(attacker)))
      .andExpect(status().isNoContent());

    me(victim).andExpect(status().isOk());
  }

  private String registeredEmail() throws Exception {
    String email = "session-" + UUID.randomUUID() + "@clinica.pe";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"%s","password":"%s","role":"PATIENT","fullName":"Luis Rojas","acceptedTerms":true}"""
        .formatted(email, PASSWORD)))
      .andExpect(status().isCreated());
    return email;
  }

  private JsonNode signIn(String email) throws Exception {
    return json(mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
      .andExpect(status().isOk()));
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
      .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
  }

  private ResultActions me(String accessToken) throws Exception {
    return mvc.perform(get("/api/v1/user-accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
  }

  private UUID currentSessionId(String accessToken) throws Exception {
    JsonNode sessions = json(mvc.perform(get("/api/v1/user-accounts/me/sessions")
      .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))).andExpect(status().isOk()));
    for (JsonNode session : sessions) {
      if (session.get("current").asBoolean()) return UUID.fromString(session.get("id").asText());
    }
    throw new AssertionError("No current session in " + sessions);
  }

  private JsonNode json(ResultActions result) throws Exception {
    return mapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
