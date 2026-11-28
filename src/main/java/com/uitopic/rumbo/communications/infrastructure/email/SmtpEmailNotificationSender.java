package com.uitopic.rumbo.communications.infrastructure.email;

import com.uitopic.rumbo.communications.application.ports.EmailNotificationSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends notification emails through the configured SMTP server. It exists only with
 * {@code rumbo.communications.email.enabled=true}, which also needs {@code spring.mail.host}.
 */
@Component
@ConditionalOnProperty(name = "rumbo.communications.email.enabled", havingValue = "true")
public class SmtpEmailNotificationSender implements EmailNotificationSender {
  private final JavaMailSender mail;
  private final String from;

  public SmtpEmailNotificationSender(JavaMailSender mail, @Value("${rumbo.communications.email.from}") String from) {
    this.mail = mail;
    this.from = from;
  }

  @Override
  public void send(String to, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body + "\n\nRumbo · Este es un mensaje automático, no respondas a este correo.");
    try {
      mail.send(message);
    } catch (MailParseException | MailPreparationException | MailAuthenticationException permanent) {
      throw new DeliveryFailure("The message cannot be sent as it is", false, permanent);
    } catch (MailException temporary) {
      throw new DeliveryFailure("The mail server did not accept the message", true, temporary);
    }
  }
}
