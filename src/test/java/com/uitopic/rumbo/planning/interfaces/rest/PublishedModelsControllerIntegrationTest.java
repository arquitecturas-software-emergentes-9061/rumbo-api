package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.valueobjects.*;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
class PublishedModelsControllerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired IndoorMapRepository maps;
  @Autowired IndoorMapCommandService commands;

  @Test void generatedOpenApiDescribesModelAndConditionalReads() throws Exception {
    var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    var api = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
    var get = api.at("/paths/~1api~1v1~1sites~1{siteId}~1published-map/get");
    org.junit.jupiter.api.Assertions.assertFalse(get.isMissingNode());
    org.junit.jupiter.api.Assertions.assertFalse(get.at("/responses/304").isMissingNode());
    org.junit.jupiter.api.Assertions.assertTrue(get.get("parameters").toString().contains("If-None-Match"));
    org.junit.jupiter.api.Assertions.assertTrue(get.at("/responses/200/content").toString().contains("ModelResource"));
    var model = api.at("/components/schemas/ModelResource/properties");
    for (String field : List.of("modelVersionId", "floors", "nodes", "connections", "destinations", "walls", "doors")) {
      org.junit.jupiter.api.Assertions.assertTrue(model.has(field), field);
    }
  }

  @Test void patientsReadOnlyPublishedVersionsAndRevalidateWithEtag() throws Exception {
    var first = draft(null);
    var patient = caller(UUID.randomUUID(), UserRole.PATIENT);
    String url = url(first.siteId());
    mvc.perform(get(url).with(patient)).andExpect(status().isNotFound());
    commands.handle(new PublishMapVersionCommand(first.siteId(), first.id(), first.modelVersionId()));
    String etag = "\"map-" + first.modelVersionId() + "\"";
    mvc.perform(get(url).with(patient)).andExpect(status().isOk())
      .andExpect(header().string("ETag", etag))
      .andExpect(jsonPath("$.status").value("PUBLISHED"))
      .andExpect(jsonPath("$.plans[0].floorId").value(first.plans().getFirst().floorId().toString()))
      .andExpect(jsonPath("$.nodes.length()").value(2))
      .andExpect(jsonPath("$.destinations[0].name").value("Reception"));
    mvc.perform(get(url).with(patient).header("If-None-Match", etag))
      .andExpect(status().isNotModified()).andExpect(content().string(""))
      .andExpect(header().stringValues("ETag", etag));
    var next = draft(first);
    mvc.perform(get(url).with(patient)).andExpect(jsonPath("$.modelVersionId").value(first.modelVersionId().toString()));
    commands.handle(new PublishMapVersionCommand(next.siteId(), next.id(), next.modelVersionId()));
    mvc.perform(get(url).with(patient).header("If-None-Match", etag)).andExpect(status().isOk())
      .andExpect(jsonPath("$.modelVersionId").value(next.modelVersionId().toString()));
  }

  @Test void administratorsCannotReadOtherOrganizationsAndAnonymousCallsAreRejected() throws Exception {
    var map = draft(null);
    commands.handle(new PublishMapVersionCommand(map.siteId(), map.id(), map.modelVersionId()));
    UUID owner = jdbc.queryForObject("SELECT owner_user_id FROM indoor_sites WHERE id = ?", UUID.class, map.siteId());
    mvc.perform(get(url(map.siteId()))).andExpect(status().isUnauthorized());
    mvc.perform(get(url(map.siteId())).with(caller(UUID.randomUUID(), UserRole.CLINIC_ADMIN)))
      .andExpect(status().isForbidden());
    mvc.perform(get(url(map.siteId())).with(caller(owner, UserRole.CLINIC_ADMIN)))
      .andExpect(status().isOk());
  }

  @Test void catalogExcludesDraftsAndScopesAdministratorSites() throws Exception {
    var published = draft(null);
    var draftOnly = draft(null);
    commands.handle(new PublishMapVersionCommand(published.siteId(), published.id(), published.modelVersionId()));
    UUID owner = jdbc.queryForObject("SELECT owner_user_id FROM indoor_sites WHERE id = ?", UUID.class, published.siteId());
    mvc.perform(get("/api/v1/published-sites")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/published-sites").with(caller(UUID.randomUUID(), UserRole.PATIENT)))
      .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(published.siteId().toString())))
      .andExpect(jsonPath("$[*].id", not(hasItem(draftOnly.siteId().toString()))));
    mvc.perform(get("/api/v1/published-sites").with(caller(owner, UserRole.CLINIC_ADMIN)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  }

  private String url(UUID site) { return "/api/v1/sites/" + site + "/published-map"; }

  private RequestPostProcessor caller(UUID id, UserRole role) {
    var principal = new AuthenticatedPrincipal(id, role, UUID.randomUUID(), null);
    return SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(principal,
        null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }

  private IndoorMap draft(IndoorMap source) {
    var map = new IndoorMap(source == null ? UUID.randomUUID() : source.id(),
        source == null ? UUID.randomUUID() : source.siteId(), UUID.randomUUID());
    com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.enterpriseSite(jdbc, map.siteId());
    UUID floor = source == null ? UUID.randomUUID() : source.plans().getFirst().floorId();
    UUID plan = source == null ? UUID.randomUUID() : source.plans().getFirst().planVersionId();
    map.addPlan(new PlanCoordinateSpace(floor, plan, 100, 100));
    map.setScale(new MapScale(floor, plan, .5));
    UUID access = UUID.randomUUID(), destination = UUID.randomUUID();
    map.addNode(access, floor, plan, 10, 10, NodeType.ACCESS);
    map.addNode(destination, floor, plan, 20, 10, NodeType.DESTINATION);
    map.addConnection(UUID.randomUUID(), access, destination, ConnectionType.CORRIDOR, 5, 5, true);
    map.addDestination(new com.uitopic.rumbo.planning.domain.model.entities.MapDestination(UUID.randomUUID(),
        map.modelVersionId(), destination, DirectoryEntryType.SERVICE, "Reception", null));
    maps.saveDraft(map);
    return map;
  }
}
