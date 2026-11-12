package com.uitopic.rumbo.profiles.domain.model.valueobjects;

import com.uitopic.rumbo.profiles.domain.exceptions.InvalidProfileImageException;

import java.util.Arrays;

/** Validated image content ready for storage, with defensive copies at its boundaries. */
public record ProfileImageFile(byte[] content, ImageFormat format) {
  public static final int MAX_SIZE_BYTES = 5_242_880;

  public ProfileImageFile {
    if (content == null || content.length == 0) {
      throw new InvalidProfileImageException(InvalidProfileImageException.EMPTY_IMAGE, "Profile image is empty");
    }
    if (content.length > MAX_SIZE_BYTES) {
      throw new InvalidProfileImageException(InvalidProfileImageException.IMAGE_TOO_LARGE, "Profile image exceeds 5 MiB");
    }
    content = content.clone();
    ImageFormat detected = ImageFormat.detect(content).orElseThrow(() -> new InvalidProfileImageException(
        InvalidProfileImageException.UNSUPPORTED_IMAGE_FORMAT, "Unsupported profile image format"));
    if (format != detected) {
      throw new InvalidProfileImageException(InvalidProfileImageException.IMAGE_TYPE_MISMATCH, "Image format contradicts content");
    }
  }

  public static ProfileImageFile of(byte[] content, String declaredContentType) {
    ProfileImageFile image = new ProfileImageFile(content, ImageFormat.detect(content).orElse(null));
    if (declaredContentType != null && !declaredContentType.isBlank()) {
      String mediaType = declaredContentType.split(";", 2)[0].trim();
      if (!image.format().mediaType().equalsIgnoreCase(mediaType)) {
        throw new InvalidProfileImageException(InvalidProfileImageException.IMAGE_TYPE_MISMATCH,
            "Declared image type contradicts content");
      }
    }
    return image;
  }

  @Override
  public byte[] content() { return content.clone(); }

  public int size() { return content.length; }

  @Override
  public boolean equals(Object other) {
    return other instanceof ProfileImageFile image && format == image.format && Arrays.equals(content, image.content);
  }

  @Override
  public int hashCode() { return 31 * Arrays.hashCode(content) + format.hashCode(); }

  @Override
  public String toString() { return "ProfileImageFile[size=" + size() + ", format=" + format + "]"; }
}
