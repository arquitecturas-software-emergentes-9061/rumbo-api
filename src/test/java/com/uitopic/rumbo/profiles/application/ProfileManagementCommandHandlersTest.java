package com.uitopic.rumbo.profiles.application;

import com.uitopic.rumbo.profiles.application.commandhandlers.*;
import com.uitopic.rumbo.profiles.application.ports.ProfileImageStorage;
import com.uitopic.rumbo.profiles.application.exceptions.ProfileImageStorageUnavailableException;
import com.uitopic.rumbo.profiles.infrastructure.storage.cloudinary.UnavailableProfileImageStorage;
import com.uitopic.rumbo.profiles.domain.exceptions.ProfileNotFoundException;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.commands.*;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.*;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileManagementCommandHandlersTest {
  ProfileRepository profiles = mock(ProfileRepository.class);
  ProfileImageStorage storage = mock(ProfileImageStorage.class);
  Clock clock = Clock.fixed(Instant.parse("2026-11-12T14:00:00Z"), ZoneOffset.UTC);
  Profile original = Profile.initialize(UUID.randomUUID(), "Ana", "PATIENT", clock.instant().minusSeconds(60));
  ProfileImage oldImage = new ProfileImage("https://example.com/old", "rumbo-ar/profile-images/old");
  ProfileImage newImage = new ProfileImage("https://example.com/new", "rumbo-ar/profile-images/new");
  ProfileImageFile file = ProfileImageFile.of(new byte[] {(byte) 255, (byte) 216, (byte) 255}, "image/jpeg");

  @BeforeEach void setup() {
    when(profiles.findByUserId(original.userId())).thenReturn(Optional.of(original));
    when(profiles.save(any())).thenAnswer(call -> call.getArgument(0));
    when(storage.store(original.userId(), file)).thenReturn(newImage);
    TransactionSynchronizationManager.initSynchronization();
  }
  @AfterEach void clear() { TransactionSynchronizationManager.clearSynchronization(); }

  @Test void updatesDataAndClearsBlankPhone() {
    var handler = new UpdateProfileCommandHandler(profiles, clock);
    Profile result = handler.handle(new UpdateProfileCommand(original.userId(), "  Maria  ", " "));
    assertThat(result.fullName().value()).isEqualTo("Maria");
    assertThat(result.phoneNumber()).isNull();
    assertThat(result.id()).isEqualTo(original.id());
  }
  @Test void missingProfileDoesNotUpload() {
    UUID missing = UUID.randomUUID();
    assertThatThrownBy(() -> new UpdateProfileCommandHandler(profiles, clock)
        .handle(new UpdateProfileCommand(missing, "Ana", null))).isInstanceOf(ProfileNotFoundException.class);
    assertThatThrownBy(() -> new ReplaceProfileImageCommandHandler(profiles, storage, clock)
        .handle(new ReplaceProfileImageCommand(missing, file))).isInstanceOf(ProfileNotFoundException.class);
    verifyNoInteractions(storage);
  }
  @Test void replacementDeletesPreviousOnlyAfterCommit() {
    when(profiles.findByUserId(original.userId())).thenReturn(Optional.of(original.replaceImage(oldImage, clock.instant())));
    new ReplaceProfileImageCommandHandler(profiles, storage, clock).handle(new ReplaceProfileImageCommand(original.userId(), file));
    verify(storage, never()).delete(anyString());
    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    verify(storage).delete(oldImage.storageKey());
    complete(TransactionSynchronization.STATUS_COMMITTED);
    verify(storage, never()).delete(newImage.storageKey());
  }
  @Test void failedSaveCompensatesUploadedImageAfterRollback() {
    when(profiles.save(any())).thenThrow(new IllegalStateException("save failed"));
    assertThatThrownBy(() -> new ReplaceProfileImageCommandHandler(profiles, storage, clock)
        .handle(new ReplaceProfileImageCommand(original.userId(), file))).isInstanceOf(IllegalStateException.class);
    complete(TransactionSynchronization.STATUS_ROLLED_BACK);
    verify(storage).delete(newImage.storageKey());
    verify(storage, never()).delete(oldImage.storageKey());
  }
  @Test void rollbackAfterSuccessfulSaveAlsoCompensates() {
    new ReplaceProfileImageCommandHandler(profiles, storage, clock).handle(new ReplaceProfileImageCommand(original.userId(), file));
    complete(TransactionSynchronization.STATUS_ROLLED_BACK);
    verify(storage).delete(newImage.storageKey());
  }
  @Test void removalIsIdempotent() {
    var handler = new RemoveProfileImageCommandHandler(profiles, storage, clock);
    assertThat(handler.handle(new RemoveProfileImageCommand(original.userId()))).isEqualTo(original);
    verify(profiles, never()).save(any());
    verify(storage, never()).delete(anyString());
  }
  @Test void removalDeletesAfterCommitAndIgnoresDeletionFailure() {
    when(profiles.findByUserId(original.userId())).thenReturn(Optional.of(original.replaceImage(oldImage, clock.instant())));
    doThrow(new IllegalStateException()).when(storage).delete(oldImage.storageKey());
    assertThat(new RemoveProfileImageCommandHandler(profiles, storage, clock)
        .handle(new RemoveProfileImageCommand(original.userId())).image()).isNull();
    verify(storage, never()).delete(anyString());
    assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    verify(storage).delete(oldImage.storageKey());
  }
  @Test void unavailableStorageFailsImageCommandsOnly() {
    var unavailable = new UnavailableProfileImageStorage();
    assertThatThrownBy(() -> new ReplaceProfileImageCommandHandler(profiles, unavailable, clock)
        .handle(new ReplaceProfileImageCommand(original.userId(), file)))
        .isInstanceOf(ProfileImageStorageUnavailableException.class);
    assertThatThrownBy(() -> new RemoveProfileImageCommandHandler(profiles, unavailable, clock)
        .handle(new RemoveProfileImageCommand(original.userId())))
        .isInstanceOf(ProfileImageStorageUnavailableException.class);
    assertThat(new UpdateProfileCommandHandler(profiles, clock)
        .handle(new UpdateProfileCommand(original.userId(), "Ana", null))).isNotNull();
  }
  private void complete(int status) {
    TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
  }
}
