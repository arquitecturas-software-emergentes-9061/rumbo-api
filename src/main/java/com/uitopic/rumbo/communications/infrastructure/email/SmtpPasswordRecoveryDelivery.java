package com.uitopic.rumbo.communications.infrastructure.email;

import com.uitopic.rumbo.communications.application.ports.PasswordRecoveryDelivery;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "rumbo.communications.recovery-mail.enabled", havingValue = "true")
public class SmtpPasswordRecoveryDelivery implements PasswordRecoveryDelivery {
  private final JavaMailSender mail;
  private final String from;
  private final URI resetPage;

  public SmtpPasswordRecoveryDelivery(JavaMailSender mail,
      @Value("${rumbo.communications.recovery-mail.from}") String from,
      @Value("${rumbo.communications.recovery-mail.reset-page}") URI resetPage) {
    this.mail = mail;
    try {
      InternetAddress address = new InternetAddress(from, true);
      address.validate();
    } catch (AddressException invalid) {
      throw new IllegalArgumentException("Recovery sender must be a valid email address");
    }
    if (!("https".equalsIgnoreCase(resetPage.getScheme()) || "http".equalsIgnoreCase(resetPage.getScheme()))
        || resetPage.getHost() == null || resetPage.getRawQuery() != null
        || resetPage.getRawFragment() != null || resetPage.getUserInfo() != null) {
      throw new IllegalArgumentException("Recovery page must be an absolute HTTP(S) URL without query or fragment");
    }
    this.from = from;
    this.resetPage = resetPage;
  }

  @Override
  public void send(String recipient, String token, Instant expiresAt) {
    String link = UriComponentsBuilder.fromUri(resetPage).queryParam("token", token)
        .build().encode().toUriString();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(recipient);
    message.setSubject("RUMBO — Recupera el acceso a tu cuenta");
    message.setText("""
        Recibimos una solicitud para restablecer tu contraseña de RUMBO.

        Abre este enlace para elegir una nueva contraseña:
        %s

        El enlace vence el %s (UTC), funciona una sola vez y al usarlo se cerrarán tus sesiones.
        En la aplicación móvil puedes pegar este enlace en «Ya tengo un enlace».
        Si no solicitaste este cambio, ignora este correo. Tu contraseña no se ha modificado.
        """.formatted(link, expiresAt));
    try {
      mail.send(message);
    } catch (MailException failure) {
      throw new PasswordRecoveryDelivery.Unavailable();
    }
  }
}
