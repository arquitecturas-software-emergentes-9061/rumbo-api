package com.uitopic.rumbo.acceptance.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Profiles and Preferences steps. PR-47 exposes no profiles REST API, so they assert on the database. */
public class ProfilesSteps {
  @Autowired ScenarioContext context;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;

  @Then("the registered user has exactly one initial profile named {string} with role {string}")
  public void theRegisteredUserHasExactlyOneInitialProfile(String name, String role) throws Exception {
    UUID userId = UUID.fromString(mapper.readTree(context.body()).get("id").asText());

    List<Map<String, Object>> rows =
      jdbc.queryForList("select * from profiles_user_profiles where user_id = ?", userId);

    assertThat(rows).hasSize(1);
    Map<String, Object> row = rows.getFirst();
    assertThat(row.get("full_name")).isEqualTo(name);
    assertThat(row.get("role")).isEqualTo(role);
    assertThat(row.get("phone_number")).isNull();
    assertThat(row.get("image_url")).isNull();
    assertThat(row.get("image_storage_key")).isNull();
  }
}
