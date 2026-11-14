package com.uitopic.rumbo.profiles.infrastructure.storage.cloudinary;

import com.cloudinary.Cloudinary;
import com.uitopic.rumbo.profiles.application.exceptions.ProfileImageStorageFailedException;
import com.uitopic.rumbo.profiles.application.ports.ProfileImageStorage;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImage;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImageFile;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CloudinaryConfiguredCondition.class)
public class CloudinaryProfileImageStorage implements ProfileImageStorage {
  public static final String FOLDER = "rumbo-ar/profile-images";
  private final Cloudinary cloudinary;

  @Autowired
  public CloudinaryProfileImageStorage(@Value("${rumbo.profiles.images.cloudinary-url}") String url) {
    this(new Cloudinary(url));
  }

  public CloudinaryProfileImageStorage(Cloudinary cloudinary) { this.cloudinary = cloudinary; }

  @Override public ProfileImage store(UUID userId, ProfileImageFile file) {
    try {
      var result = cloudinary.uploader().upload(file.content(), Map.of("folder", FOLDER,
          "public_id", UUID.randomUUID().toString(), "resource_type", "image", "overwrite", false));
      return new ProfileImage((String) result.get("secure_url"), (String) result.get("public_id"));
    } catch (Exception ignored) { throw new ProfileImageStorageFailedException(); }
  }

  @Override public void delete(String storageKey) {
    if (storageKey == null || !storageKey.startsWith(FOLDER + "/")) return;
    try { cloudinary.uploader().destroy(storageKey, Map.of("resource_type", "image")); }
    catch (Exception ignored) { throw new ProfileImageStorageFailedException(); }
  }
}
