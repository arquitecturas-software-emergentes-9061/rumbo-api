package com.uitopic.rumbo.profiles.infrastructure;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.uitopic.rumbo.profiles.infrastructure.storage.cloudinary.CloudinaryProfileImageStorage;
import com.uitopic.rumbo.profiles.application.exceptions.ProfileImageStorageFailedException;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImageFile;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudinaryProfileImageStorageTest {
  Cloudinary cloudinary = mock(Cloudinary.class);
  Uploader uploader = mock(Uploader.class);
  CloudinaryProfileImageStorage storage = new CloudinaryProfileImageStorage(cloudinary);
  ProfileImageFile file = ProfileImageFile.of(new byte[] {(byte) 255, (byte) 216, (byte) 255}, "image/jpeg");

  @Test void uploadsWithRestrictedOptionsAndMapsResponse() throws Exception {
    when(cloudinary.uploader()).thenReturn(uploader);
    when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://example.com/image",
        "public_id", "rumbo-ar/profile-images/key"));
    var result = storage.store(UUID.randomUUID(), file);
    assertThat(result.url()).isEqualTo("https://example.com/image");
    assertThat(result.storageKey()).isEqualTo("rumbo-ar/profile-images/key");
    ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
    verify(uploader).upload(any(), options.capture());
    assertThat(options.getValue()).containsEntry("folder", "rumbo-ar/profile-images")
        .containsEntry("resource_type", "image").containsEntry("overwrite", false);
    assertThatCode(() -> UUID.fromString((String) options.getValue().get("public_id"))).doesNotThrowAnyException();
  }
  @Test void refusesKeysOutsideOwnedFolder() {
    storage.delete("another-folder/key");
    storage.delete("rumbo-ar/profile-images-other/key");
    storage.delete(null);
    verifyNoInteractions(cloudinary, uploader);
  }
  @Test void destroysOwnedAssetAsImage() throws Exception {
    when(cloudinary.uploader()).thenReturn(uploader);
    storage.delete("rumbo-ar/profile-images/key");
    verify(uploader).destroy("rumbo-ar/profile-images/key", Map.of("resource_type", "image"));
  }
  @Test void hidesSdkErrors() throws Exception {
    when(cloudinary.uploader()).thenReturn(uploader);
    when(uploader.upload(any(), anyMap())).thenThrow(new IOException());
    assertThatThrownBy(() -> storage.store(UUID.randomUUID(), file))
        .isInstanceOf(ProfileImageStorageFailedException.class).hasCause(null);
  }
}
