package com.uitopic.rumbo.iam.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.iam.domain.model.events.PasswordResetRequestedEvent;
import com.uitopic.rumbo.iam.domain.model.events.UserPasswordChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-28: recovery through an emailed link, with PostgreSQL and Redis in containers. The email itself belongs to
 * Communications, so the link is read from the {@link PasswordResetRequestedEvent} that IAM publishes.
 */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@RecordApplicationEvents
class PasswordRecoveryIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";
  private static final String NEW_PASSWORD = "Rumbo2027";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired ApplicationEvents events;

  @Test
  void anUnknownEmailGetsTheSameAnswerAndNoLink() throws Exception {
    requestReset("nadie-" + UUID.randomUUID() + "@clinica.pe").andExpect(status().isAccepted());
    requestReset("not-an-email").andExpect(status().isAccepted());

    assertThat(events.stream(PasswordResetRequestedEvent.class)).isEmpty();
  }

  @Test
  void theNewPasswordWorksAndEverySessionEnds() throws Exception {
    String email = registeredEmail();
    JsonNode phone = signIn(email, PASSWORD);
    JsonNode laptop = signIn(email, PASSWORD);

    requestReset(email).andExpect(status().isAccepted());
    confirm(linkSentTo(email), NEW_PASSWORD).andExpect(status().isNoContent());

    me(phone.get("accessToken").asText()).andExpect(status().isUnauthorized());
    me(laptop.get("accessToken").asText()).andExpect(status().isUnauthorized());
    refresh(laptop.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    signInRequest(email, PASSWORD).andExpect(status().isUnauthorized());
    signInRequest(email, NEW_PASSWORD).andExpect(status().isOk());
    assertThat(events.stream(UserPasswordChangedEvent.class)).hasSize(1);
  }

  @Test
  void aLinkWorksOnlyOnce() throws Exception {
    String email = registeredEmail();
    requestReset(email);
    String link = linkSentTo(email);

    confirm(link, NEW_PASSWORD).andExpect(status().isNoContent());

    confirm(link, "Rumbo2028").andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
  }

  @Test
  void aWeakPasswordDoesNotWasteTheLink() throws Exception {
    String email = registeredEmail();
    requestReset(email);
    String link = linkSentTo(email);

    confirm(link, "short").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));

    confirm(link, NEW_PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  void aNewRequestInvalidatesThePreviousLink() throws Exception {
    String email = registeredEmail();
    requestReset(email);
    String first = linkSentTo(email);
    requestReset(email);
    String second = linkSentTo(email);

    confirm(first, NEW_PASSWORD).andExpect(status().isBadRequest());
    confirm(second, NEW_PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  void recoveringTheAccountLiftsAFailedAttemptsLock() throws Exception {
    String email = registeredEmail();
    for (int attempt = 0; attempt < 5; attempt++) signInRequest(email, "Wrong-Password-1");
    signInRequest(email, PASSWORD).andExpect(status().isLocked());

    requestReset(email);
    confirm(linkSentTo(email), NEW_PASSWORD).andExpect(status().isNoContent());

    signInRequest(email, NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void anInventedLinkIsRejected() throws Exception {
    confirm("invented-token", NEW_PASSWORD).andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
  }

  private String linkSentTo(String email) {
    return events.stream(PasswordResetRequestedEvent.class).filter(event -> event.email().equals(email))
      .reduce((first, last) -> last).orElseThrow().resetToken();
  }

  private ResultActions requestReset(String email) throws Exception {
    return mvc.perform(post("/api/v1/password-recovery/requests").contentType(MediaType.APPLICATION_JSON)
      .content("{\"email\":\"%s\"}".formatted(email)));
  }

  private ResultActions confirm(String resetToken, String newPassword) throws Exception {
    return mvc.perform(post("/api/v1/password-recovery/confirmations").contentType(MediaType.APPLICATION_JSON)
      .content("{\"resetToken\":\"%s\",\"newPassword\":\"%s\"}".formatted(resetToken, newPassword)));
  }

  private String registeredEmail() throws Exception {
    String email = "admin-" + UUID.randomUUID() + "@clinica.pe";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"%s","password":"%s","role":"CLINIC_ADMIN","fullName":"Ana Torres","clinicName":"Clinica Central",
         "acceptedTerms":true}""".formatted(email, PASSWORD)))
      .andExpect(status().isCreated());
    return email;
  }

  private ResultActions signInRequest(String email, String password) throws Exception {
    return mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
      .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
  }

  private JsonNode signIn(String email, String password) throws Exception {
    return mapper.readTree(signInRequest(email, password).andExpect(status().isOk()).andReturn().getResponse()
      .getContentAsString());
  }

  private ResultActions me(String accessToken) throws Exception {
    return mvc.perform(get("/api/v1/user-accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
      .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
  }
}
