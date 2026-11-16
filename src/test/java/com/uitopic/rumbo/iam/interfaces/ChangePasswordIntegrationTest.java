package com.uitopic.rumbo.iam.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Changing the password from the profile, with PostgreSQL and Redis in containers. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class ChangePasswordIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";
  private static final String NEW_PASSWORD = "Rumbo2027";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  @Test
  void theCurrentSessionStaysAndTheOthersEnd() throws Exception {
    String email = registeredEmail();
    String laptop = accessToken(signIn(email, PASSWORD));
    String phone = accessToken(signIn(email, PASSWORD));

    change(laptop, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    me(laptop).andExpect(status().isOk());
    me(phone).andExpect(status().isUnauthorized());
    signInRequest(email, PASSWORD).andExpect(status().isUnauthorized());
    signInRequest(email, NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void aWrongCurrentPasswordChangesNothing() throws Exception {
    String email = registeredEmail();
    String token = accessToken(signIn(email, PASSWORD));

    change(token, "Wrong-Password-1", NEW_PASSWORD).andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INCORRECT_CURRENT_PASSWORD"))
      .andExpect(jsonPath("$.errors.currentPassword").exists());

    signInRequest(email, PASSWORD).andExpect(status().isOk());
  }

  @Test
  void theNewPasswordMustMeetThePolicy() throws Exception {
    String token = accessToken(signIn(registeredEmail(), PASSWORD));

    change(token, PASSWORD, "short").andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
  }

  @Test
  void itRequiresASignedInUser() throws Exception {
    mvc.perform(patch("/api/v1/user-accounts/me/password").contentType(MediaType.APPLICATION_JSON)
        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(PASSWORD, NEW_PASSWORD)))
      .andExpect(status().isUnauthorized());
  }

  private ResultActions change(String accessToken, String current, String next) throws Exception {
    return mvc.perform(patch("/api/v1/user-accounts/me/password")
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON)
      .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
  }

  private String registeredEmail() throws Exception {
    String email = "patient-" + UUID.randomUUID() + "@mail.com";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"%s","password":"%s","role":"PATIENT","fullName":"Luis Rojas","acceptedTerms":true}"""
        .formatted(email, PASSWORD)))
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

  private static String accessToken(JsonNode credentials) {
    return credentials.get("accessToken").asText();
  }

  private ResultActions me(String accessToken) throws Exception {
    return mvc.perform(get("/api/v1/user-accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
  }
}
