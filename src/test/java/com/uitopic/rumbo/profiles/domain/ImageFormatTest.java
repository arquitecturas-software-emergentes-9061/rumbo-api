package com.uitopic.rumbo.profiles.domain;

import com.uitopic.rumbo.profiles.domain.model.valueobjects.ImageFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies detection by complete signatures rather than file metadata. */
class ImageFormatTest {
  static byte[] jpeg() { return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; }

  static byte[] png() { return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}; }

  @Test
  void detectsSupportedSignaturesWithTrailingContent() {
    assertThat(ImageFormat.detect(Arrays.copyOf(jpeg(), 20))).contains(ImageFormat.JPEG);
    assertThat(ImageFormat.detect(Arrays.copyOf(png(), 20))).contains(ImageFormat.PNG);
    assertThat(ImageFormat.JPEG.mediaType()).isEqualTo("image/jpeg");
    assertThat(ImageFormat.PNG.mediaType()).isEqualTo("image/png");
  }

  @Test
  void rejectsMissingTruncatedAndUnsupportedSignatures() {
    assertThat(ImageFormat.detect(null)).isEmpty();
    for (byte[] signature : new byte[][] {jpeg(), png()}) {
      for (int length = 0; length < signature.length; length++) {
        assertThat(ImageFormat.detect(Arrays.copyOf(signature, length))).isEmpty();
      }
    }
    for (String content : new String[] {"GIF89a", "<svg", "RIFF....WEBP", "random bytes"}) {
      assertThat(ImageFormat.detect(content.getBytes(StandardCharsets.US_ASCII))).isEmpty();
    }
  }
}
