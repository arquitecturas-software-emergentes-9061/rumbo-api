package com.uitopic.rumbo.profiles.interfaces;

import com.uitopic.rumbo.profiles.interfaces.rest.ProfileUploadLimitTestController;
import com.uitopic.rumbo.profiles.interfaces.rest.ProfileUploadSizeExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfileUploadSizeExceptionHandlerTest {
  private static final long FLOOR_PLAN_LIMIT = 25L * 1024 * 1024;

  @Test void translatesTheServletUploadLimitForProfileEndpoints() throws Exception {
    mvc(new ProfileUploadLimitTestController()).perform(get("/profiles-limit"))
        .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
  }

  @Test void leavesTheUploadLimitOfOtherContextsAlone() throws Exception {
    // e.g. floor plans (25 MiB) keep their own PLAN_FILE_TOO_LARGE translation
    mvc(new OtherContextController()).perform(get("/other-limit"))
        .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").doesNotExist());
  }

  private static MockMvc mvc(Object controller) {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new ProfileUploadSizeExceptionHandler()).build();
  }

  @RestController
  @Profile("upload-limit-test")
  static class OtherContextController {
    @GetMapping("/other-limit")
    void limit() { throw new MaxUploadSizeExceededException(FLOOR_PLAN_LIMIT); }
  }
}
