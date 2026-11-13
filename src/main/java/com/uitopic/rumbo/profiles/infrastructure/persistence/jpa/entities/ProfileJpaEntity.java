package com.uitopic.rumbo.profiles.infrastructure.persistence.jpa.entities;

import com.uitopic.rumbo.shared.infrastructure.persistence.jpa.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of {@code profiles_user_profiles}. Only the mapper converts it to and from the {@code Profile} aggregate.
 * Columns must stay aligned with {@code V202611121200__profiles_create_user_profiles.sql}.
 */
@Entity
@Table(name = "profiles_user_profiles")
public class ProfileJpaEntity extends AuditableJpaEntity {
  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "full_name", nullable = false, length = 255)
  private String fullName;

  @Column(nullable = false, length = 20)
  private String role;

  @Column(name = "phone_number", length = 30)
  private String phoneNumber;

  @Column(name = "image_url", length = 2048)
  private String imageUrl;

  @Column(name = "image_storage_key", length = 255)
  private String imageStorageKey;

  @Column(name = "avoid_stairs", nullable = false)
  private boolean avoidStairs;
  @Column(name = "simpler_route", nullable = false)
  private boolean simplerRoute;
  @Column(name = "voice_guidance", nullable = false)
  private boolean voiceGuidance;
  @Column(name = "preferences_updated_at")
  private Instant preferencesUpdatedAt;

  public boolean isAvoidStairs() { return avoidStairs; }
  public void setAvoidStairs(boolean value) { avoidStairs = value; }
  public boolean isSimplerRoute() { return simplerRoute; }
  public void setSimplerRoute(boolean value) { simplerRoute = value; }
  public boolean isVoiceGuidance() { return voiceGuidance; }
  public void setVoiceGuidance(boolean value) { voiceGuidance = value; }
  public Instant getPreferencesUpdatedAt() { return preferencesUpdatedAt; }
  public void setPreferencesUpdatedAt(Instant value) { preferencesUpdatedAt = value; }

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getUserId() { return userId; }

  public void setUserId(UUID userId) { this.userId = userId; }

  public String getFullName() { return fullName; }

  public void setFullName(String fullName) { this.fullName = fullName; }

  public String getRole() { return role; }

  public void setRole(String role) { this.role = role; }

  public String getPhoneNumber() { return phoneNumber; }

  public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

  public String getImageUrl() { return imageUrl; }

  public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

  public String getImageStorageKey() { return imageStorageKey; }

  public void setImageStorageKey(String imageStorageKey) { this.imageStorageKey = imageStorageKey; }

  public void setCreatedAt(Instant createdAt) { restoreCreatedAt(createdAt); }
}
