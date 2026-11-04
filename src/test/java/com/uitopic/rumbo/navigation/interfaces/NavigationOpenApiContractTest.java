package com.uitopic.rumbo.navigation.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PR-229: the actual generated contract, not only a Markdown example. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class NavigationOpenApiContractTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  @Test void publishesRouteInputAndOutputExamplesAndErrorsInOpenApi() throws Exception {
    String response = mvc.perform(get("/v3/api-docs"))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    JsonNode api = mapper.readTree(response);
    JsonNode route = api.at("/paths/~1api~1v1~1navigations~1route/post");
    assertFalse(route.isMissingNode(), "Documented route must exist");
    assertTrue(route.at("/requestBody/content/application~1json/examples").isObject(), route.toPrettyString());
    assertTrue(route.at("/responses/200/content/application~1json/examples").isObject());
    assertFalse(route.at("/responses/422").isMissingNode());
    assertFalse(route.at("/responses/400").isMissingNode());
    assertEquals("application/json", route.at("/requestBody/content").fieldNames().next());
    assertFalse(api.at("/paths/~1api~1v1~1navigations~1destinations/get").isMissingNode());
  }
}
