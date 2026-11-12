package com.uitopic.rumbo.profiles.application.commandhandlers;

import com.uitopic.rumbo.profiles.application.ports.ProfileImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Provider errors are deliberately excluded from logs because they may contain credentials. */
final class ProfileImageCleanup {
  private static final Logger LOG = LoggerFactory.getLogger(ProfileImageCleanup.class);
  private ProfileImageCleanup() {}

  static void schedule(ProfileImageStorage storage, String previous, String uploaded) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { deleteQuietly(storage, previous); }
      @Override public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) deleteQuietly(storage, uploaded);
      }
    });
  }

  static void deleteQuietly(ProfileImageStorage storage, String key) {
    if (key == null) return;
    try { storage.delete(key); }
    catch (RuntimeException ignored) { LOG.warn("Could not delete profile image with storageKey {}", key); }
  }
}
