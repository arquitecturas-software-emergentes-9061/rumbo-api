package com.uitopic.rumbo.profiles.interfaces;

import com.uitopic.rumbo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class RoutePreferencesIntegrationTest extends ProfileManagementTestSupport {
  private static final String PATH = "/api/v1/profiles/me/preferences";
  private static final String VALUES = """
      {"avoidStairs":true,"simplerRoute":false,"voiceGuidance":true}
      """;
  private static final String DEFAULTS = """
      {"avoidStairs":false,"simplerRoute":false,"voiceGuidance":false}
      """;

  private ResultActions read(Caller caller) throws Exception {
    return mvc.perform(get(PATH).header("Authorization", "Bearer " + caller.token()));
  }

  private ResultActions write(Caller caller, String body) throws Exception {
    return mvc.perform(put(PATH).header("Authorization", "Bearer " + caller.token())
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test void persistsAcrossSignInsAndIncludesPreferencesInProfile() throws Exception {
    Caller caller = register("PATIENT");
    read(caller).andExpect(status().isOk())
        .andExpect(jsonPath("$.avoidStairs").value(false))
        .andExpect(jsonPath("$.simplerRoute").value(false))
        .andExpect(jsonPath("$.voiceGuidance").value(false))
        .andExpect(jsonPath("$.configured").value(false));
    assertThat(row(caller).get("preferences_updated_at")).isNull();
    write(caller, VALUES).andExpect(status().isOk())
        .andExpect(jsonPath("$.avoidStairs").value(true))
        .andExpect(jsonPath("$.simplerRoute").value(false))
        .andExpect(jsonPath("$.voiceGuidance").value(true))
        .andExpect(jsonPath("$.configured").value(true));
    assertThat(row(caller).get("preferences_updated_at")).isNotNull();
    Caller next = signIn(caller.id(), caller.email(), caller.password());
    assertThat(next.token()).isNotEqualTo(caller.token());
    read(next).andExpect(status().isOk())
        .andExpect(jsonPath("$.avoidStairs").value(true))
        .andExpect(jsonPath("$.simplerRoute").value(false))
        .andExpect(jsonPath("$.voiceGuidance").value(true))
        .andExpect(jsonPath("$.configured").value(true));
    mvc.perform(get("/api/v1/profiles/me").header("Authorization", "Bearer " + next.token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.preferences.avoidStairs").value(true))
        .andExpect(jsonPath("$.preferences.simplerRoute").value(false))
        .andExpect(jsonPath("$.preferences.voiceGuidance").value(true));
    write(next, DEFAULTS).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true));
    read(next).andExpect(status().isOk()).andExpect(jsonPath("$.avoidStairs").value(false))
        .andExpect(jsonPath("$.configured").value(true));
  }

  @ParameterizedTest
  @CsvSource({"avoidStairs,missing", "simplerRoute,missing", "voiceGuidance,missing",
      "avoidStairs,null", "simplerRoute,null", "voiceGuidance,null"})
  void missingOrNullFieldDoesNotChangeRow(String field, String kind) throws Exception {
    Caller caller = register("PATIENT");
    write(caller, VALUES).andExpect(status().isOk());
    var before = row(caller);
    var body = mapper.readTree(VALUES);
    if (kind.equals("missing")) ((com.fasterxml.jackson.databind.node.ObjectNode) body).remove(field);
    else ((com.fasterxml.jackson.databind.node.ObjectNode) body).putNull(field);
    write(caller, mapper.writeValueAsString(body)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors." + field).isString());
    assertThat(row(caller)).isEqualTo(before);
  }

  @Test void malformedBooleanDoesNotChangeRow() throws Exception {
    Caller caller = register("PATIENT");
    write(caller, VALUES).andExpect(status().isOk());
    var before = row(caller);
    write(caller, """
        {"avoidStairs":"yes","simplerRoute":false,"voiceGuidance":true}
        """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    assertThat(row(caller)).isEqualTo(before);
  }

  @Test void accountsAreIsolated() throws Exception {
    Caller first = register("PATIENT");
    Caller second = register("CLINIC_ADMIN");
    var secondBefore = row(second);
    write(first, VALUES).andExpect(status().isOk());
    assertThat(row(second)).isEqualTo(secondBefore);
    read(second).andExpect(status().isOk()).andExpect(jsonPath("$.avoidStairs").value(false))
        .andExpect(jsonPath("$.voiceGuidance").value(false)).andExpect(jsonPath("$.configured").value(false));
    write(second, """
        {"avoidStairs":false,"simplerRoute":true,"voiceGuidance":false}
        """).andExpect(status().isOk());
    read(first).andExpect(status().isOk()).andExpect(jsonPath("$.avoidStairs").value(true))
        .andExpect(jsonPath("$.simplerRoute").value(false)).andExpect(jsonPath("$.voiceGuidance").value(true));
  }

  @Test void requiresTokenForBothEndpoints() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(VALUES))
        .andExpect(status().isUnauthorized());
  }

  @Test void deletedProfileReturns404ForBothEndpoints() throws Exception {
    Caller caller = register("PATIENT");
    jdbc.update("delete from profiles_user_profiles where user_id = ?", caller.id());
    read(caller).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
    write(caller, VALUES).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
  }
}
