package com.uitopic.rumbo.profiles.domain.model.valueobjects;

/**
 * Optional profile image: where it is served from and the key used to replace or delete it in storage.
 *
 * @param url public address of the image, at most {@value #MAX_URL_LENGTH} characters
 * @param storageKey key of the stored file, at most {@value #MAX_STORAGE_KEY_LENGTH} characters
 */
public record ProfileImage(String url, String storageKey) {
  public static final int MAX_URL_LENGTH = 2048;
  public static final int MAX_STORAGE_KEY_LENGTH = 255;

  public ProfileImage {
    if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
      throw new IllegalArgumentException("Invalid profile image url");
    }
    if (storageKey == null || storageKey.isBlank() || storageKey.length() > MAX_STORAGE_KEY_LENGTH) {
      throw new IllegalArgumentException("Invalid profile image storage key");
    }
  }
}
