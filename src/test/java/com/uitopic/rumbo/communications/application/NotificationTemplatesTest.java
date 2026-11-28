package com.uitopic.rumbo.communications.application;

import com.uitopic.rumbo.communications.application.templates.NotificationTemplates;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationPriority;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplatesTest {
  private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");

  @Test
  void everyTypeHasATitleAndMessageInBothLanguages() {
    for (NotificationType type : NotificationType.values()) {
      Notification notification = Notification.inApp(UUID.randomUUID(), type, null, NOW);
      for (Locale locale : new Locale[] {NotificationTemplates.DEFAULT_LOCALE, Locale.ENGLISH}) {
        NotificationTemplates.Rendered text = NotificationTemplates.render(notification, locale);
        assertThat(text.title()).as(type + " " + locale).isNotBlank();
        assertThat(text.message()).as(type + " " + locale).isNotBlank().doesNotContain("  ");
      }
    }
  }

  @Test
  void anIncidentNamesItsCategoryInTheLanguageOfTheCaller() {
    Notification incident = Notification.create(UUID.randomUUID(), NotificationType.INCIDENT_REPORTED,
        NotificationChannel.IN_APP, "BLOCKED_PATH", UUID.randomUUID(), UUID.randomUUID(), NotificationPriority.HIGH,
        NOW);

    assertThat(NotificationTemplates.render(incident, NotificationTemplates.DEFAULT_LOCALE).message())
        .contains("un paso bloqueado");
    assertThat(NotificationTemplates.render(incident, Locale.ENGLISH).message()).contains("a blocked path");
  }

  @Test
  void anUnknownCategoryStillReadsAsAProblemOnTheRoute() {
    Notification incident = Notification.inApp(UUID.randomUUID(), NotificationType.INCIDENT_REPORTED, "FIRE", NOW);

    assertThat(NotificationTemplates.render(incident, Locale.ENGLISH).message())
        .contains("a problem on the route").doesNotContain("FIRE");
  }

  @Test
  void aLockShowsTheUnlockTimeOfLima() {
    Notification locked = Notification.inApp(UUID.randomUUID(), NotificationType.ACCOUNT_LOCKED,
        "2026-11-27T15:30:00Z", NOW);

    assertThat(NotificationTemplates.render(locked, NotificationTemplates.DEFAULT_LOCALE).message())
        .contains("27/11/2026 10:30");
  }

  @Test
  void theLanguageComesFromTheFirstAcceptLanguageAndDefaultsToSpanish() {
    assertThat(NotificationTemplates.fromAcceptLanguage("en-US,en;q=0.9,es;q=0.8")).isEqualTo(Locale.ENGLISH);
    assertThat(NotificationTemplates.fromAcceptLanguage("es-PE,es;q=0.9,en;q=0.8"))
        .isEqualTo(NotificationTemplates.DEFAULT_LOCALE);
    assertThat(NotificationTemplates.fromAcceptLanguage(null)).isEqualTo(NotificationTemplates.DEFAULT_LOCALE);
    assertThat(NotificationTemplates.fromAcceptLanguage(";;;")).isEqualTo(NotificationTemplates.DEFAULT_LOCALE);
    assertThat(NotificationTemplates.fromAcceptLanguage("fr-FR")).isEqualTo(NotificationTemplates.DEFAULT_LOCALE);
  }
}
