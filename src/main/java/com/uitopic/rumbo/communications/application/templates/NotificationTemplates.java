package com.uitopic.rumbo.communications.application.templates;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/**
 * Texts of the notifications (WI-US31-03), in Spanish by default and in English. The datum of {@code detail} is
 * inserted here, so clients can show a notification without their own catalogue and the email uses the same words.
 */
public final class NotificationTemplates {
  /** Language of the texts when the caller does not ask for one. */
  public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es-PE");
  private static final ZoneId CLINIC_TIME = ZoneId.of("America/Lima");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final Map<String, String> INCIDENTS_ES = Map.of(
      "BLOCKED_PATH", "un paso bloqueado", "WRONG_DIRECTIONS", "indicaciones equivocadas",
      "MAP_MISMATCH", "un mapa que no coincide con el lugar", "SIGNAL_LOST", "pérdida de ubicación",
      "ACCESSIBILITY_BARRIER", "una barrera de accesibilidad", "OTHER", "un problema en la ruta");
  private static final Map<String, String> INCIDENTS_EN = Map.of(
      "BLOCKED_PATH", "a blocked path", "WRONG_DIRECTIONS", "wrong directions",
      "MAP_MISMATCH", "a map that does not match the place", "SIGNAL_LOST", "a lost position",
      "ACCESSIBILITY_BARRIER", "an accessibility barrier", "OTHER", "a problem on the route");

  private NotificationTemplates() { }

  /**
   * Language asked for in an {@code Accept-Language} header: English when its first language is English, Spanish
   * otherwise. The server's own default locale is never used, so a deployment abroad still answers in Spanish.
   */
  public static Locale fromAcceptLanguage(String header) {
    if (header == null || header.isBlank()) return DEFAULT_LOCALE;
    try {
      var ranges = Locale.LanguageRange.parse(header);
      if (!ranges.isEmpty() && ranges.get(0).getRange().toLowerCase(Locale.ROOT).startsWith("en")) {
        return Locale.ENGLISH;
      }
    } catch (IllegalArgumentException malformed) {
      return DEFAULT_LOCALE;
    }
    return DEFAULT_LOCALE;
  }

  /** Title and message of the notification in the language of {@code locale}; Spanish unless it asks for English. */
  public static Rendered render(Notification notification, Locale locale) {
    boolean english = locale != null && "en".equals(locale.getLanguage());
    return english ? english(notification.type(), notification.detail())
        : spanish(notification.type(), notification.detail());
  }

  private static Rendered spanish(NotificationType type, String detail) {
    return switch (type) {
      case WELCOME -> new Rendered("Te damos la bienvenida a Rumbo",
          "Tu cuenta está lista. Ya puedes buscar destinos y guiarte dentro de la clínica.");
      case ACCOUNT_LOCKED -> new Rendered("Cuenta bloqueada temporalmente",
          "Hubo varios intentos fallidos de inicio de sesión. Podrás volver a intentarlo desde el "
              + time(detail, "unos minutos") + ".");
      case PASSWORD_CHANGED -> new Rendered("Tu contraseña cambió",
          "Si no fuiste tú, recupera el acceso desde «¿Olvidaste tu contraseña?».");
      case SUBSCRIPTION_ACTIVATED -> new Rendered("Suscripción activa",
          "Tu plan " + plan(detail) + "ya está activo.");
      case INCIDENT_REPORTED -> new Rendered("Nuevo reporte en tu sede",
          "Un visitante reportó " + INCIDENTS_ES.getOrDefault(orDefault(detail, "OTHER"), INCIDENTS_ES.get("OTHER"))
              + ". Revísalo en Reportes de navegación.");
      case MAP_PUBLISHED -> new Rendered("Mapa publicado",
          "Se publicó una nueva versión del mapa de tu sede. La app ya guía con esta versión.");
    };
  }

  private static Rendered english(NotificationType type, String detail) {
    return switch (type) {
      case WELCOME -> new Rendered("Welcome to Rumbo",
          "Your account is ready. You can now search destinations and find your way inside the clinic.");
      case ACCOUNT_LOCKED -> new Rendered("Account temporarily locked",
          "There were several failed sign-in attempts. You can try again from " + time(detail, "a few minutes") + ".");
      case PASSWORD_CHANGED -> new Rendered("Your password changed",
          "If it was not you, recover your access from \"Forgot your password?\".");
      case SUBSCRIPTION_ACTIVATED -> new Rendered("Subscription active",
          "Your " + plan(detail) + "plan is now active.");
      case INCIDENT_REPORTED -> new Rendered("New report at your site",
          "A visitor reported " + INCIDENTS_EN.getOrDefault(orDefault(detail, "OTHER"), INCIDENTS_EN.get("OTHER"))
              + ". Review it in Navigation reports.");
      case MAP_PUBLISHED -> new Rendered("Map published",
          "A new version of the map of your site was published. The app now guides with it.");
    };
  }

  private static String time(String instant, String fallback) {
    if (instant == null) return fallback;
    try {
      return TIME.format(Instant.parse(instant).atZone(CLINIC_TIME));
    } catch (DateTimeParseException unreadable) {
      return fallback;
    }
  }

  private static String plan(String code) {
    return code == null || code.isBlank() ? "" : code.trim() + " ";
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  /** Text ready to show or to send. */
  public record Rendered(String title, String message) { }
}
