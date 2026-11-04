package com.uitopic.rumbo.acceptance.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generic HTTP steps reusable by every bounded context. Context-specific steps live next to their feature
 * (for example {@code IamSteps}) and share the last response through {@link ScenarioContext}.
 */
public class ApiSteps {
  @LocalServerPort int port;
  @Autowired ObjectMapper mapper;
  @Autowired ScenarioContext context;

  @Before
  public void configureClient() {
    context.reset(RestClient.builder().baseUrl("http://localhost:" + port)
      .defaultStatusHandler(status -> true, (request, response) -> { })
      .build());
  }

  @When("I send a GET request to {string}")
  public void get(String path) {
    context.send(context.client().get().uri(context.resolve(path)));
  }

  @When("I send a POST request to {string} with body:")
  public void post(String path, String body) {
    context.send(context.client().post().uri(context.resolve(path)).contentType(MediaType.APPLICATION_JSON)
      .body(body));
  }

  @When("I send an authenticated POST request to {string} with body:")
  public void authenticatedPost(String path, String body) {
    context.send(context.client().post().uri(context.resolve(path)).contentType(MediaType.APPLICATION_JSON)
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + context.accessToken()).body(body));
  }

  @When("I send an authenticated GET request to {string}")
  public void authenticatedGet(String path) {
    context.send(context.client().get().uri(context.resolve(path))
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + context.accessToken()));
  }

  @Then("the response status is {int}")
  public void status(int expected) {
    assertThat(context.status()).as(context.body()).isEqualTo(expected);
  }

  @Then("the response field {string} is {string}")
  public void field(String pointer, String expected) throws Exception {
    JsonNode json = mapper.readTree(context.body());
    assertThat(json.at(pointer).asText()).as(context.body()).isEqualTo(expected);
  }

  @Then("the response field {string} is present")
  public void fieldPresent(String pointer) throws Exception {
    JsonNode json = mapper.readTree(context.body());
    assertThat(json.at(pointer).isMissingNode() || json.at(pointer).isNull()).as(context.body()).isFalse();
  }

  @Then("I remember the response field {string} as {string}")
  public void rememberField(String pointer, String key) throws Exception {
    JsonNode value = mapper.readTree(context.body()).at(pointer);
    assertThat(value.isMissingNode()).as(context.body()).isFalse();
    context.remember(key, value.asText());
  }

  @Then("the response body is an empty list")
  public void emptyList() throws Exception {
    JsonNode json = mapper.readTree(context.body());
    assertThat(json.isArray() && json.isEmpty()).as(context.body()).isTrue();
  }

  @Then("the response does not contain {string}")
  public void doesNotContain(String text) {
    assertThat(context.body()).doesNotContain(text);
  }
}
