package com.uitopic.rumbo.profiles.domain.model.valueobjects;

import java.util.Optional;

/** Supported profile image formats, detected only from their content signatures. */
public enum ImageFormat {
  JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
  PNG("image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

  private final String mediaType;
  private final byte[] signature;

  ImageFormat(String mediaType, byte[] signature) {
    this.mediaType = mediaType;
    this.signature = signature;
  }

  public String mediaType() { return mediaType; }

  public static Optional<ImageFormat> detect(byte[] content) {
    if (content == null) return Optional.empty();
    for (ImageFormat format : values()) {
      if (content.length < format.signature.length) continue;
      boolean matches = true;
      for (int i = 0; i < format.signature.length; i++) {
        if (content[i] != format.signature[i]) {
          matches = false;
          break;
        }
      }
      if (matches) return Optional.of(format);
    }
    return Optional.empty();
  }
}
