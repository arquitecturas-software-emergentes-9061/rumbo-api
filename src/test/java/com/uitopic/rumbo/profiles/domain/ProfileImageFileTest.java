package com.uitopic.rumbo.profiles.domain;

import com.uitopic.rumbo.profiles.domain.exceptions.InvalidProfileImageException;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ImageFormat;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImageFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies profile image restrictions, error precedence and immutable value semantics. */
class ProfileImageFileTest {
  @Test
  void acceptsSupportedImagesAndOptionalNormalizedTypes() {
    for (ImageFormat format : ImageFormat.values()) {
      byte[] content = format == ImageFormat.JPEG ? ImageFormatTest.jpeg() : ImageFormatTest.png();
      for (String type : new String[] {format.mediaType(), null, "  ", format.mediaType().toUpperCase(Locale.ROOT),
          " " + format.mediaType() + "; charset=x"}) {
        ProfileImageFile image = ProfileImageFile.of(content, type);
        assertThat(image.format()).isEqualTo(format);
        assertThat(image.size()).isEqualTo(content.length);
        assertThat(image.content()).containsExactly(content);
      }
    }
  }

  @Test
  void acceptsInclusiveSizeLimitAndRejectsLargerContentBeforeFormatOrType() {
    byte[] content = Arrays.copyOf(ImageFormatTest.png(), ProfileImageFile.MAX_SIZE_BYTES);
    assertThat(ProfileImageFile.of(content, null).size()).isEqualTo(5_242_880);
    rejects(new byte[5_242_881], "image/gif", "IMAGE_TOO_LARGE");
  }

  @Test
  void rejectsEmptyImagesBeforeDeclaredType() {
    rejects(null, "image/gif", "EMPTY_IMAGE");
    rejects(new byte[0], "image/png", "EMPTY_IMAGE");
  }

  @Test
  void rejectsUnsupportedContentBeforeDeclaredType() {
    for (String content : new String[] {"GIF89a", "<svg", "RIFF....WEBP", "random bytes"}) {
      rejects(content.getBytes(StandardCharsets.US_ASCII), "image/png", "UNSUPPORTED_IMAGE_FORMAT");
    }
    rejects(new byte[] {(byte) 0xFF, (byte) 0xD8}, null, "UNSUPPORTED_IMAGE_FORMAT");
  }

  @Test
  void rejectsContradictoryAndUnknownDeclaredTypes() {
    rejects(ImageFormatTest.jpeg(), "image/png", "IMAGE_TYPE_MISMATCH");
    for (String type : new String[] {"image/jpeg", "application/octet-stream", "image/gif", "image/jpg", "; charset=x"}) {
      rejects(ImageFormatTest.png(), type, "IMAGE_TYPE_MISMATCH");
    }
  }

  @Test
  void copiesInputAndAccessorAndUsesContentValueSemantics() {
    byte[] content = ImageFormatTest.png();
    ProfileImageFile image = ProfileImageFile.of(content, null);
    ProfileImageFile equal = ProfileImageFile.of(content.clone(), "image/png");
    content[0] = 0;
    image.content()[1] = 0;
    assertThat(image.content()).containsExactly(ImageFormatTest.png());
    assertThat(image).isEqualTo(equal);
    assertThat(image.hashCode()).isEqualTo(equal.hashCode());
    assertThat(image).isNotEqualTo(ProfileImageFile.of(ImageFormatTest.jpeg(), null));
    assertThat(image).isNotEqualTo(ProfileImageFile.of(Arrays.copyOf(ImageFormatTest.png(), 9), null));
    assertThat(image.toString()).isEqualTo("ProfileImageFile[size=8, format=PNG]")
        .doesNotContain(Arrays.toString(ImageFormatTest.png()));
  }

  @Test
  void canonicalConstructorAlsoValidatesAndCopiesContent() {
    byte[] content = ImageFormatTest.png();
    ProfileImageFile image = new ProfileImageFile(content, ImageFormat.PNG);
    content[0] = 0;
    assertThat(image.content()).containsExactly(ImageFormatTest.png());
    assertThatThrownBy(() -> new ProfileImageFile(ImageFormatTest.png(), ImageFormat.JPEG))
        .isInstanceOf(InvalidProfileImageException.class)
        .satisfies(error -> assertThat(((InvalidProfileImageException) error).code()).isEqualTo("IMAGE_TYPE_MISMATCH"));
  }

  private static void rejects(byte[] content, String type, String code) {
    assertThatThrownBy(() -> ProfileImageFile.of(content, type))
        .isInstanceOf(InvalidProfileImageException.class)
        .satisfies(error -> assertThat(((InvalidProfileImageException) error).code()).isEqualTo(code));
  }
}
