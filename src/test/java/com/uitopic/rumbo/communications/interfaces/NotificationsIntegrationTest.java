package com.uitopic.rumbo.communications.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Notification center over HTTP: the welcome notice comes from a real sign-up and no account sees another's. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class NotificationsIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  record Caller(UUID id, String token) {}

  Caller register(String role) throws Exception {
    String email = "notifications-" + UUID.randomUUID() + "@example.com";
    String password = UUID.randomUUID() + "aA1!";
    String signup = mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("email", email, "password", password, "role", role,
            "fullName", "Ana Torres", "clinicName", "Test clinic", "acceptedTerms", true))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String signin = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return new Caller(UUID.fromString(mapper.readTree(signup).get("id").asText()),
        mapper.readTree(signin).get("accessToken").asText());
  }

  String auth(Caller caller) { return "Bearer " + caller.token(); }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
  }

  @Test
  void aSignUpLeavesAWelcomeNoticeThatCanBeReadAndTracked() throws Exception {
    Caller caller = register("PATIENT");

    String body = mvc.perform(get("/api/v1/notifications").header("Authorization", auth(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].type").value("WELCOME"))
        .andExpect(jsonPath("$.items[0].channel").value("IN_APP"))
        .andExpect(jsonPath("$.items[0].read").value(false))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.unread").value(1))
        .andReturn().getResponse().getContentAsString();
    JsonNode item = mapper.readTree(body).get("items").get(0);
    String id = item.get("id").asText();

    mvc.perform(get("/api/v1/notifications/" + id + "/delivery").header("Authorization", auth(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notificationId").value(id))
        .andExpect(jsonPath("$.channel").value("IN_APP"))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.finalState").value(true))
        .andExpect(jsonPath("$.attempts").value(1));

    mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", auth(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.read").value(true));
    mvc.perform(get("/api/v1/notifications").param("unread", "true").header("Authorization", auth(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.unread").value(0));
  }

  @Test
  void noAccountSeesOrChangesAnotherAccountsNotifications() throws Exception {
    Caller owner = register("CLINIC_ADMIN");
    Caller intruder = register("PATIENT");
    String id = mapper.readTree(mvc.perform(get("/api/v1/notifications").header("Authorization", auth(owner)))
        .andReturn().getResponse().getContentAsString()).get("items").get(0).get("id").asText();

    mvc.perform(get("/api/v1/notifications/" + id).header("Authorization", auth(intruder)))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    mvc.perform(get("/api/v1/notifications/" + id + "/delivery").header("Authorization", auth(intruder)))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", auth(intruder)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/notifications/" + id).header("Authorization", auth(owner)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(false));
  }

  @Test
  void readAllMarksOnlyTheCallersNotifications() throws Exception {
    Caller caller = register("PATIENT");
    Caller other = register("PATIENT");

    mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", auth(caller)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
    mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", auth(caller)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(0));
    mvc.perform(get("/api/v1/notifications").header("Authorization", auth(other)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(1));
  }

  @Test
  void clampsOutOfRangePaging() throws Exception {
    Caller caller = register("PATIENT");

    String body = mvc.perform(get("/api/v1/notifications").param("page", "-4").param("size", "9999")
        .header("Authorization", auth(caller))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(mapper.readTree(body).get("page").asInt()).isZero();
    assertThat(mapper.readTree(body).get("size").asInt()).isEqualTo(100);
  }
}
