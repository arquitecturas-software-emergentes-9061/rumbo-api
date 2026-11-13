package com.uitopic.rumbo.profiles.interfaces;

import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.profiles.application.ports.ProfileImageStorage;
import com.uitopic.rumbo.profiles.application.exceptions.*;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class ProfileManagementIntegrationTest extends ProfileManagementTestSupport {
  @MockitoBean ProfileImageStorage storage;

  @BeforeEach void storage() {
    when(storage.store(any(), any())).thenAnswer(call -> {
      String key = "rumbo-ar/profile-images/" + UUID.randomUUID();
      return new ProfileImage("https://example.com/" + key, key);
    });
  }
  @ParameterizedTest @ValueSource(strings = {"PATIENT", "CLINIC_ADMIN"})
  void updatesOnlyCallersRowAndOmitsStorageKey(String role) throws Exception {
    Caller caller = register(role);
    Caller other = register("PATIENT");
    Object id = row(caller).get("id");
    mvc.perform(put("/api/v1/profiles/me").header("Authorization", "Bearer " + caller.token())
        .contentType(MediaType.APPLICATION_JSON).content("""
        {"fullName":"  Maria Torres  ","phoneNumber":"+51 (123) 456-789"}
        """))
        .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Maria Torres"))
        .andExpect(jsonPath("$.userId").value(caller.id().toString()))
        .andExpect(jsonPath("$.role").value(role)).andExpect(jsonPath("$.storageKey").doesNotExist());
    assertThat(row(caller).get("id")).isEqualTo(id);
    assertThat(row(caller).get("phone_number")).isEqualTo("+51 (123) 456-789");
    assertThat(row(other).get("full_name")).isEqualTo("Ana Torres");
    mvc.perform(put("/api/v1/profiles/me").header("Authorization", "Bearer " + caller.token())
        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Maria\",\"phoneNumber\":\" \"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.phoneNumber").isEmpty());
  }
  @ParameterizedTest @ValueSource(strings = {
      "{}", "{\"fullName\":\" \"}", "{\"fullName\":\"Ana\",\"phoneNumber\":\"abc123\"}"})
  void invalidDataHasFieldErrors(String body) throws Exception {
    Caller caller = register("PATIENT");
    mvc.perform(put("/api/v1/profiles/me").header("Authorization", "Bearer " + caller.token())
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.errors").isMap());
  }
  @Test void acceptsMaximumPhoneAndNameAfterTrimming() throws Exception {
    Caller caller = register("PATIENT");
    String name = "a".repeat(255);
    String phone = "+" + "1".repeat(29);
    mvc.perform(put("/api/v1/profiles/me").header("Authorization", "Bearer " + caller.token())
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
            Map.of("fullName", " " + name + " ", "phoneNumber", phone))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value(name))
        .andExpect(jsonPath("$.phoneNumber").value(phone));
    assertThat(row(caller).get("phone_number")).isEqualTo(phone);
    mvc.perform(put("/api/v1/profiles/me").header("Authorization", "Bearer " + caller.token())
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
            Map.of("fullName", name, "phoneNumber", phone + "1"))))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.phoneNumber").isString());
    mvc.perform(put("/api/v1/profiles/me").header("Authorization", "Bearer " + caller.token())
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
            Map.of("fullName", name + "a"))))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.fullName").isString());
  }
  @Test void requiresAuthentication() throws Exception {
    mvc.perform(put("/api/v1/profiles/me").contentType(MediaType.APPLICATION_JSON)
        .content("{\"fullName\":\"Ana\"}")).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/profiles/me/image")).andExpect(status().isUnauthorized());
    mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/profiles/me/image"))
        .andExpect(status().isUnauthorized());
  }
  @Test void uploadsReplacesAndRemovesAfterCommit() throws Exception {
    Caller caller = register("PATIENT");
    upload(caller, png(), "image/png").andExpect(status().isOk())
        .andExpect(jsonPath("$.imageUrl").isString()).andExpect(jsonPath("$.storageKey").doesNotExist());
    var first = row(caller);
    assertThat(first.get("image_url")).isNotNull();
    String oldKey = (String) first.get("image_storage_key");
    assertThat(oldKey).startsWith("rumbo-ar/profile-images/");
    upload(caller, png(), "image/png").andExpect(status().isOk());
    verify(storage).delete(oldKey);
    String nextKey = (String) row(caller).get("image_storage_key");
    assertThat(nextKey).isNotEqualTo(oldKey);
    remove(caller).andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").isEmpty());
    verify(storage).delete(nextKey);
    assertThat(row(caller).get("image_url")).isNull();
    assertThat(row(caller).get("image_storage_key")).isNull();
    remove(caller).andExpect(status().isOk());
    verify(storage, times(2)).delete(anyString());
  }
  @Test void validatesImageAndMissingPart() throws Exception {
    Caller caller = register("PATIENT");
    upload(caller, new byte[] {(byte) 255, (byte) 216, (byte) 255}, "image/png")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_TYPE_MISMATCH"));
    upload(caller, new byte[0], "image/png").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EMPTY_IMAGE"));
    upload(caller, new byte[] {1, 2, 3}, "image/png").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_FORMAT"));
    upload(caller, new byte[ProfileImageFile.MAX_SIZE_BYTES + 1], "image/png")
        .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
    mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/profiles/me/image")
        .header("Authorization", "Bearer " + caller.token())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    verify(storage, never()).store(any(), any());
  }
  @Test void missingProfileReturns404() throws Exception {
    Caller caller = register("PATIENT");
    jdbc.update("delete from profiles_user_profiles where user_id = ?", caller.id());
    mvc.perform(put("/api/v1/profiles/me").header("Authorization", "Bearer " + caller.token())
        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Ana\"}"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
  }
  @Test void unavailableAndFailedStorageHaveSanitizedErrors() throws Exception {
    Caller caller = register("PATIENT");
    when(storage.store(any(), any())).thenThrow(new ProfileImageStorageUnavailableException());
    upload(caller, png(), "image/png").andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_STORAGE_UNAVAILABLE"));
    doThrow(new ProfileImageStorageUnavailableException()).when(storage).requireAvailable();
    remove(caller).andExpect(status().isServiceUnavailable());
    mvc.perform(put("/api/v1/profiles/me").header("Authorization", "Bearer " + caller.token())
        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Ana\"}")).andExpect(status().isOk());
    doThrow(new ProfileImageStorageFailedException()).when(storage).store(any(), any());
    upload(caller, png(), "image/png").andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_STORAGE_FAILED"));
  }
  @Test void translatesServletUploadLimitWithGlobalAdvice() throws Exception {
    var advice = new com.uitopic.rumbo.profiles.interfaces.rest.ProfileUploadSizeExceptionHandler();
    org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new LimitController())
        .setControllerAdvice(advice).build().perform(get("/limit"))
        .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
  }
  @org.springframework.web.bind.annotation.RestController
  @org.springframework.context.annotation.Profile("upload-limit-test")
  static class LimitController {
    @org.springframework.web.bind.annotation.GetMapping("/limit")
    void limit() { throw new MaxUploadSizeExceededException(ProfileImageFile.MAX_SIZE_BYTES); }
  }
}
