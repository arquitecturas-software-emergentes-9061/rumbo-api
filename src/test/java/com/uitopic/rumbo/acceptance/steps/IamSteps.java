package com.uitopic.rumbo.acceptance.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.acceptance.support.RecoveryMailbox;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps of Identity and Access Control. Emails written as {@code <name>@...} in a feature get a unique suffix per
 * scenario, so scenarios never collide on the database shared by the suite.
 */
public class IamSteps {
  @Autowired ScenarioContext context;
  @Autowired ObjectMapper mapper;
  @Autowired RecoveryMailbox mailbox;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Given("a clinic administrator registered as {string} with password {string}")
  public void registeredAdmin(String email, String password) {
    signUp(email, password);
    assertThat(context.status()).as(context.body()).isEqualTo(201);
    com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.activeEnterprise(jdbc,
        UUID.fromString(jdbc.queryForObject("SELECT id::text FROM identity_users WHERE email = ?",
            String.class, unique(email))));
  }

  @Given("a patient registered as {string} with password {string}")
  public void registeredPatient(String email, String password) {
    context.send(context.client().post().uri("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
      .body("""
        {"email":"%s","password":"%s","role":"PATIENT","fullName":"Luis Rojas","acceptedTerms":true}"""
        .formatted(unique(email), password)));
    assertThat(context.status()).as(context.body()).isEqualTo(201);
  }

  @When("a clinic administrator signs up as {string} with password {string}")
  public void signUp(String email, String password) {
    context.send(context.client().post().uri("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
      .body("""
        {"email":"%s","password":"%s","role":"CLINIC_ADMIN","fullName":"Ana Torres",
         "clinicName":"Clinica Central","acceptedTerms":true}""".formatted(unique(email), password)));
  }

  @When("they sign in as {string} with password {string}")
  public void signIn(String email, String password) throws Exception {
    context.send(context.client().post().uri("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
      .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(unique(email), password)));
    rememberCredentials();
  }

  @When("they refresh the session")
  public void refresh() throws Exception {
    String current = context.recall("refreshToken");
    sendRefresh(current);
    if (context.status() == 200) context.remember("previousRefreshToken", current);
    rememberCredentials();
  }

  @When("they refresh with the previous refresh token")
  public void refreshWithPrevious() {
    sendRefresh(context.recall("previousRefreshToken"));
  }

  @When("they sign out")
  public void signOut() {
    context.send(context.client().post().uri("/api/v1/auth/sign-out")
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + context.accessToken()));
  }

  @When("they fail to sign in as {string} {int} times")
  public void failRepeatedly(String email, int times) throws Exception {
    for (int attempt = 0; attempt < times; attempt++) signIn(email, "Wrong-Password-1");
  }

  @When("they request a password recovery for {string}")
  public void requestRecovery(String email) {
    context.send(context.client().post().uri("/api/v1/password-recovery/requests")
      .contentType(MediaType.APPLICATION_JSON).body("{\"email\":\"%s\"}".formatted(unique(email))));
  }

  @When("they open the link sent to {string} and choose the password {string}")
  public void resetWithLink(String email, String newPassword) {
    String link = mailbox.lastLinkSentTo(unique(email)).orElseThrow(() -> new AssertionError("No link was sent"));
    context.remember("resetToken", link);
    sendResetConfirmation(link, newPassword);
  }

  @When("they open the same link again and choose the password {string}")
  public void reuseLink(String newPassword) {
    sendResetConfirmation(context.recall("resetToken"), newPassword);
  }

  @Then("no recovery link was sent to {string}")
  public void noLink(String email) {
    assertThat(mailbox.lastLinkSentTo(unique(email))).isEmpty();
  }

  @Then("the response email is {string}")
  public void responseEmail(String email) throws Exception {
    assertThat(mapper.readTree(context.body()).get("email").asText()).isEqualTo(unique(email));
  }

  private void sendResetConfirmation(String resetToken, String newPassword) {
    context.send(context.client().post().uri("/api/v1/password-recovery/confirmations")
      .contentType(MediaType.APPLICATION_JSON)
      .body("{\"resetToken\":\"%s\",\"newPassword\":\"%s\"}".formatted(resetToken, newPassword)));
  }

  private void sendRefresh(String refreshToken) {
    context.send(context.client().post().uri("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
      .body("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
  }

  private void rememberCredentials() throws Exception {
    if (context.status() != 200) return;
    var credentials = mapper.readTree(context.body());
    context.remember("accessToken", credentials.get("accessToken").asText());
    context.remember("refreshToken", credentials.get("refreshToken").asText());
  }

  private String unique(String email) {
    String suffix = context.recall("emailSuffix");
    if (suffix == null) {
      suffix = UUID.randomUUID().toString().substring(0, 8);
      context.remember("emailSuffix", suffix);
    }
    int at = email.indexOf('@');
    return at < 0 ? email : email.substring(0, at) + "+" + suffix + email.substring(at);
  }
}
