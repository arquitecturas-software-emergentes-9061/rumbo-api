package com.uitopic.rumbo.acceptance.steps;

import io.cucumber.spring.ScenarioScope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/** State shared by the step classes of one scenario: HTTP client, last response and remembered values. */
@Component
@ScenarioScope
public class ScenarioContext {
  private RestClient client;
  private int status;
  private String body = "";
  private final Map<String, String> values = new HashMap<>();

  void reset(RestClient client) {
    this.client = client;
    this.status = 0;
    this.body = "";
    this.values.clear();
  }

  public RestClient client() { return client; }

  public void send(RestClient.RequestHeadersSpec<?> request) {
    ResponseEntity<String> response = request.retrieve().toEntity(String.class);
    status = response.getStatusCode().value();
    body = response.getBody() == null ? "" : response.getBody();
  }

  public int status() { return status; }

  public String body() { return body; }

  public void remember(String key, String value) { values.put(key, value); }

  public String recall(String key) { return values.get(key); }

  public String accessToken() { return values.get("accessToken"); }
}
