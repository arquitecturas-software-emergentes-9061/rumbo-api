package com.uitopic.rumbo.profiles.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class ProfileManagementTestSupport {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  record Caller(UUID id, String token, String email, String password) {}

  Caller register(String role) throws Exception {
    String email = "profiles-update-" + UUID.randomUUID() + "@example.com";
    // Generated test-only password stays in memory.
    String password = UUID.randomUUID() + "aA1!";
    String signup = mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("email", email, "password", password, "role", role,
            "fullName", "Ana Torres", "clinicName", "Test clinic", "acceptedTerms", true))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return signIn(UUID.fromString(mapper.readTree(signup).get("id").asText()), email, password);
  }
  Caller signIn(UUID id, String email, String password) throws Exception {
    String signin = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return new Caller(id, mapper.readTree(signin).get("accessToken").asText(), email, password);
  }
  ResultActions upload(Caller caller, byte[] bytes, String type) throws Exception {
    return mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/profiles/me/image")
        .file(new MockMultipartFile("image", "image", type, bytes))
        .header("Authorization", "Bearer " + caller.token()));
  }
  ResultActions remove(Caller caller) throws Exception {
    return mvc.perform(delete("/api/v1/profiles/me/image").header("Authorization", "Bearer " + caller.token()));
  }
  Map<String, Object> row(Caller caller) {
    return jdbc.queryForMap("select * from profiles_user_profiles where user_id = ?", caller.id());
  }
  static byte[] png() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }
}
