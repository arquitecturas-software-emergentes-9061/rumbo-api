package com.uitopic.rumbo.subscriptions.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
abstract class CheckoutIntegrationSupport {
  static final String CHECKOUT = "/api/v1/subscriptions/checkout-sessions";
  static final String ORDERS = "/api/v1/subscriptions/payment-orders/";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  protected String token(String role) throws Exception {
    String email = "checkout-" + UUID.randomUUID() + "@example.test";
    String password = "Test-" + UUID.randomUUID();
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(Map.of("email", email, "password", password, "role", role,
        "fullName", "Checkout Tester", "clinicName", "Checkout Clinic", "acceptedTerms", true))))
      .andExpect(status().isCreated());
    String response = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return mapper.readTree(response).get("accessToken").asText();
  }
}
