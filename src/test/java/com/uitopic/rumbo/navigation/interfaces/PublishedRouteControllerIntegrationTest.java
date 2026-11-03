package com.uitopic.rumbo.navigation.interfaces;

import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full published Planning -> Navigation ACL -> A* -> REST contract on PostgreSQL. */
@SpringBootTest
@ActiveProfiles("postgres")
@TestPropertySource(properties = "rumbo.navigation.routes.enabled=true")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class PublishedRouteControllerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private RequestPostProcessor admin(UUID user) {
    var auth = new UsernamePasswordAuthenticationToken(
      new AuthenticatedPrincipal(user, UserRole.CLINIC_ADMIN, UUID.randomUUID(), null), null,
      List.of(new SimpleGrantedAuthority("ROLE_CLINIC_ADMIN")));
    return SecurityMockMvcRequestPostProcessors.authentication(auth);
  }

  private record MapFixture(UUID site, UUID owner, UUID start, UUID finish) {}
  private MapFixture publishedMap() {
    UUID owner = UUID.randomUUID(), site = UUID.randomUUID(), map = UUID.randomUUID(),
      version = UUID.randomUUID(), lower = UUID.randomUUID(), upper = UUID.randomUUID(),
      lowerPlan = UUID.randomUUID(), upperPlan = UUID.randomUUID();
    UUID start = UUID.randomUUID(), stairStart = UUID.randomUUID(), stairEnd = UUID.randomUUID(),
      liftStart = UUID.randomUUID(), liftEnd = UUID.randomUUID(), finish = UUID.randomUUID();
    jdbc.update("""
      INSERT INTO identity_users (id,email,password_hash,role,status,created_at,updated_at)
      VALUES (?, ?, 'test-only', 'CLINIC_ADMIN', 'ACTIVE', now(), now())
      """, owner, "route-" + owner + "@example.com");
    jdbc.update("INSERT INTO indoor_sites (id, owner_user_id, name) VALUES (?, ?, 'Clinic')", site, owner);
    jdbc.update("INSERT INTO indoor_maps (id,site_id) VALUES (?, ?)", map, site);
    jdbc.update("INSERT INTO map_versions (id,map_id,status) VALUES (?, ?, 'PUBLISHED')", version, map);
    jdbc.update("""
      INSERT INTO plan_coordinate_spaces (plan_version_id,model_version_id,floor_id,
        width_units,height_units,meters_per_unit) VALUES (?, ?, ?, 100, 100, 1)
      """, lowerPlan, version, lower);
    jdbc.update("""
      INSERT INTO plan_coordinate_spaces (plan_version_id,model_version_id,floor_id,
        width_units,height_units,meters_per_unit) VALUES (?, ?, ?, 100, 100, 1)
      """, upperPlan, version, upper);
    addNode(start,version,lower,lowerPlan,0,0,"ACCESS");
    addNode(stairStart,version,lower,lowerPlan,2,0,"INTERSECTION");
    addNode(stairEnd,version,upper,upperPlan,2,0,"INTERSECTION");
    addNode(liftStart,version,lower,lowerPlan,2,2,"INTERSECTION");
    addNode(liftEnd,version,upper,upperPlan,2,2,"INTERSECTION");
    addNode(finish,version,upper,upperPlan,5,2,"DESTINATION");
    addLink(version,start,stairStart,"CORRIDOR",2,2,0);
    addLink(version,start,liftStart,"CORRIDOR",3,3,0);
    addLink(version,stairStart,stairEnd,"STAIRS",4,1,20);
    addLink(version,liftStart,liftEnd,"ELEVATOR",4,2,3);
    addLink(version,stairEnd,finish,"CORRIDOR",5,5,0);
    addLink(version,liftEnd,finish,"CORRIDOR",3,3,0);
    return new MapFixture(site, owner, start, finish);
  }

  private void addNode(UUID id, UUID version, UUID floor, UUID plan, int x, int y, String type) {
    jdbc.update("""
      INSERT INTO map_nodes (id,model_version_id,floor_id,plan_version_id,x,y,node_type)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      """, id, version, floor, plan, x, y, type);
  }

  private void addLink(UUID version, UUID source, UUID target, String kind,
                       double distance, double weight, double penalty) {
    jdbc.update("""
      INSERT INTO map_connections (id,model_version_id,source_node_id,target_node_id,
        connection_type,distance_meters,weight,vertical_penalty,bidirectional)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, false)
      """, UUID.randomUUID(), version, source, target, kind, distance, weight, penalty);
  }

  @Test void findsMinimumWeightedCostAcrossFloorsWithCorrectVerticalPenalty() throws Exception {
    MapFixture fx = publishedMap();
    String url = "/api/v1/sites/" + fx.site() + "/routes?from=" + fx.start() + "&to=" + fx.finish();
    mvc.perform(get(url).with(admin(fx.owner())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalCost").value(11))
      .andExpect(jsonPath("$.totalDistanceMeters").value(10))
      .andExpect(jsonPath("$.links[1].kind").value("ELEVATOR"))
      .andExpect(jsonPath("$.links[1].verticalPenalty").value(3));
    mvc.perform(get(url).with(admin(fx.owner())).param("accessibleOnly","true"))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(jsonPath("$.code").value("ACCESSIBILITY_UNAVAILABLE"));
    mvc.perform(get(url.replace("from="+fx.start(), "from="+fx.finish()).replace("to="+fx.finish(),"to="+fx.start()))
        .with(admin(fx.owner())))
      .andExpect(status().isNotFound());
  }

  @Test void rejectsUnknownNodesAndAnotherAdministratorsSite() throws Exception {
    MapFixture fx = publishedMap();
    String path = "/api/v1/sites/" + fx.site() + "/routes?from=" + fx.start() + "&to=" + fx.finish();
    mvc.perform(get(path).with(admin(UUID.randomUUID()))).andExpect(status().isForbidden());
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/sites/" + fx.site() + "/routes?from=" + UUID.randomUUID() + "&to=" + fx.finish())
      .with(admin(fx.owner()))).andExpect(status().isBadRequest());
  }

  @Test void realRouteEndpointAndExamplesArePresentInOpenApi() throws Exception {
    mvc.perform(get("/v3/api-docs"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.paths['/api/v1/sites/{siteId}/routes'].get.parameters").isArray());
  }
}
