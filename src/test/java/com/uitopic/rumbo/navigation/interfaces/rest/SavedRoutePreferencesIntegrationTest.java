package com.uitopic.rumbo.navigation.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.iam.infrastructure.security.SiteOwnerGuard;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionAccessibility;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * TS-29 end to end (WI-TS29-02 and WI-TS29-05): the preferences a user saved in Profiles and Preferences change the
 * route that Navigation calculates, with no parameter in the request.
 */
@SpringBootTest(properties = "rumbo.navigation.routes.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
class SavedRoutePreferencesIntegrationTest {
  private static final String AVOID_STAIRS = """
      {"avoidStairs":true,"simplerRoute":false,"voiceGuidance":false}
      """;

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired IndoorMapRepository maps;
  @Autowired IndoorMapCommandService commands;
  @MockitoBean SiteOwnerGuard siteOwnerGuard;

  @BeforeEach
  void allowFixtureSites() {
    when(siteOwnerGuard.isOwner(any(), any())).thenReturn(true);
  }

  @Test
  void savedPreferenceToAvoidStairsTakesTheElevator() throws Exception {
    Fixture fixture = publishedFloors(true);
    String token = signedUpAdmin();

    route(fixture, token).andExpect(status().isOk()).andExpect(jsonPath("$.links[0].type").value("STAIRS"));

    savePreferences(token, AVOID_STAIRS);
    route(fixture, token).andExpect(status().isOk()).andExpect(jsonPath("$.links[0].type").value("ELEVATOR"));
  }

  @Test
  void savedPreferenceWithOnlyStairsIsReportedAsIncompatible() throws Exception {
    Fixture fixture = publishedFloors(false);
    String token = signedUpAdmin();
    savePreferences(token, AVOID_STAIRS);

    route(fixture, token).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NO_COMPATIBLE_ROUTE"));
  }

  @Test
  void anotherUserWithoutSavedPreferencesKeepsTheCheapestRoute() throws Exception {
    Fixture fixture = publishedFloors(true);
    savePreferences(signedUpAdmin(), AVOID_STAIRS);

    route(fixture, signedUpAdmin()).andExpect(status().isOk())
        .andExpect(jsonPath("$.links[0].type").value("STAIRS"));
  }

  private ResultActions route(Fixture fixture, String token) throws Exception {
    return mvc.perform(get("/api/v1/sites/{siteId}/routes", fixture.siteId())
        .param("from", fixture.startId().toString()).param("to", fixture.goalId().toString())
        .header("Authorization", "Bearer " + token));
  }

  private void savePreferences(String token, String body) throws Exception {
    mvc.perform(put("/api/v1/profiles/me/preferences").header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
  }

  private String signedUpAdmin() throws Exception {
    String email = "routes-" + UUID.randomUUID() + "@example.com";
    // Generated test-only password that stays in memory.
    String password = UUID.randomUUID() + "aA1!";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("email", email, "password", password, "role", "CLINIC_ADMIN",
            "fullName", "Rosa Quispe", "clinicName", "Clinica de prueba", "acceptedTerms", true))))
        .andExpect(status().isCreated());
    String signIn = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return mapper.readTree(signIn).get("accessToken").asText();
  }

  /** Two floors joined by cheap stairs and, when asked, by a more expensive elevator. */
  private Fixture publishedFloors(boolean withElevator) {
    UUID siteId = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), siteId, UUID.randomUUID());
    com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.enterpriseSite(jdbc, siteId);
    UUID lowerFloor = UUID.randomUUID();
    UUID lowerPlan = UUID.randomUUID();
    UUID upperFloor = UUID.randomUUID();
    UUID upperPlan = UUID.randomUUID();
    map.addPlan(new PlanCoordinateSpace(lowerFloor, lowerPlan, 100, 100));
    map.setScale(new MapScale(lowerFloor, lowerPlan, 1));
    map.addPlan(new PlanCoordinateSpace(upperFloor, upperPlan, 100, 100));
    map.setScale(new MapScale(upperFloor, upperPlan, 1));
    UUID startId = UUID.randomUUID();
    UUID goalId = UUID.randomUUID();
    map.addNode(startId, lowerFloor, lowerPlan, 1, 2, NodeType.ACCESS);
    map.addNode(goalId, upperFloor, upperPlan, 2, 2, NodeType.DESTINATION);
    map.addConnection(UUID.randomUUID(), startId, goalId, ConnectionType.STAIRS, 2, 2, 1,
        ConnectionAccessibility.INACCESSIBLE, true);
    if (withElevator) {
      map.addConnection(UUID.randomUUID(), startId, goalId, ConnectionType.ELEVATOR, 5, 7, 3,
          ConnectionAccessibility.ACCESSIBLE, true);
    }
    maps.saveDraft(map);
    commands.handle(new PublishMapVersionCommand(siteId, map.id(), map.modelVersionId()));
    return new Fixture(siteId, startId, goalId);
  }

  private record Fixture(UUID siteId, UUID startId, UUID goalId) {}
}
