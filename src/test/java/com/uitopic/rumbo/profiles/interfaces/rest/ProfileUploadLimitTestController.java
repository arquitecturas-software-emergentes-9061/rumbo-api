package com.uitopic.rumbo.profiles.interfaces.rest;

import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImageFile;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Test-only controller living in the profiles REST package, like every profile endpoint. */
@RestController
@Profile("upload-limit-test")
public class ProfileUploadLimitTestController {
  @GetMapping("/profiles-limit")
  void limit() { throw new MaxUploadSizeExceededException(ProfileImageFile.MAX_SIZE_BYTES); }
}
