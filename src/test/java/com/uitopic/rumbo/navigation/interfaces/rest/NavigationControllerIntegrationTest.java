package com.uitopic.rumbo.navigation.interfaces.rest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class NavigationControllerIntegrationTest {
  @Autowired MockMvc mvc;
  @Test void exposesFilteredDestinations() throws Exception {
    mvc.perform(get("/api/v1/navigations/destinations")
      .param("siteId","00000000-0000-4000-8000-000000000001").param("query","cardiologia"))
      .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Consultorio 101"));
  }
  @Test void calculatesRouteForDemo() throws Exception {
    mvc.perform(post("/api/v1/navigations/route").contentType(MediaType.APPLICATION_JSON).content("""
      {"siteId":"00000000-0000-4000-8000-000000000001",
       "startFloorId":"00000000-0000-4000-8000-000000000002",
       "startNodeId":"00000000-0000-4000-8000-000000000010",
       "destinationFloorId":"00000000-0000-4000-8000-000000000002",
       "destinationNodeId":"00000000-0000-4000-8000-000000000011", "accessibleOnly":true}
      """))
      .andExpect(status().isOk()).andExpect(jsonPath("$.totalDistanceMeters").value(8.0));
  }

  @Test void rejectsMalformedSiteIdentifier() throws Exception {
    mvc.perform(get("/api/v1/navigations/destinations").param("siteId", "invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Invalid or missing request field"));
  }

  @Test void rejectsRouteThroughInaccessibleDemoLink() throws Exception {
    mvc.perform(post("/api/v1/navigations/route").contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"siteId":"00000000-0000-4000-8000-000000000001",
           "startFloorId":"00000000-0000-4000-8000-000000000002",
           "startNodeId":"00000000-0000-4000-8000-000000000010",
           "destinationFloorId":"00000000-0000-4000-8000-000000000002",
           "destinationNodeId":"00000000-0000-4000-8000-000000000013",
           "accessibleOnly":true}
          """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").exists());
  }
}
