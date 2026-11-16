package com.uitopic.rumbo.profiles.interfaces;

import com.uitopic.rumbo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_URL", matches = "cloudinary://.+")
class CloudinaryProfileImageLiveIntegrationTest extends ProfileManagementTestSupport {
  @Test void storesRealImageAndCleansUpThroughApi() throws Exception {
    Caller caller = register("PATIENT");
    try {
      upload(caller, png(), "image/png").andExpect(status().isOk());
      var stored = row(caller);
      assertThat((String) stored.get("image_url")).startsWith("https://res.cloudinary.com/");
      assertThat((String) stored.get("image_storage_key")).startsWith("rumbo-ar/profile-images/");
    } finally {
      remove(caller).andExpect(status().isOk());
    }
    assertThat(row(caller).get("image_url")).isNull();
    assertThat(row(caller).get("image_storage_key")).isNull();
  }
}
