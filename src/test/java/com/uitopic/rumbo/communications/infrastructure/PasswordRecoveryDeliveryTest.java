package com.uitopic.rumbo.communications.infrastructure;

import com.uitopic.rumbo.communications.application.eventhandlers.PasswordRecoveryEmailHandler;
import com.uitopic.rumbo.communications.application.ports.PasswordRecoveryDelivery;
import com.uitopic.rumbo.communications.infrastructure.email.SmtpPasswordRecoveryDelivery;
import com.uitopic.rumbo.iam.domain.model.events.PasswordResetRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordRecoveryDeliveryTest {
  @Test
  void rejectsUnsafeOrMalformedLinkConfiguration() {
    var mail = mock(JavaMailSender.class);
    for (String url : new String[]{"/relative", "javascript:alert(1)", "https://rumbo.test/reset?token=old",
        "https://rumbo.test/reset#fragment", "https://user:password@rumbo.test/reset"}) {
      assertThatIllegalArgumentException().isThrownBy(() -> new SmtpPasswordRecoveryDelivery(mail, "admin@rumbo.test", URI.create(url)));
    }
    assertThatIllegalArgumentException().isThrownBy(() -> new SmtpPasswordRecoveryDelivery(mail, "invalid", URI.create("https://rumbo.test/reset")));
  }

  @Test
  void transportFailureIsRedactedAndDoesNotEscapeTheEventListener() {
    var mail = mock(JavaMailSender.class);
    doThrow(new MailSendException("SMTP recipient=private token=secret")).when(mail).send(any(SimpleMailMessage.class));
    var delivery = new SmtpPasswordRecoveryDelivery(mail, "admin@rumbo.test", URI.create("https://rumbo.test/reset"));
    assertThatThrownBy(() -> delivery.send("private@rumbo.test", "secret", Instant.now()))
        .isInstanceOf(PasswordRecoveryDelivery.Unavailable.class)
        .hasMessage("Password recovery delivery unavailable").hasNoCause();
    var listener = new PasswordRecoveryEmailHandler(delivery);
    assertThatCode(() -> listener.on(new PasswordResetRequestedEvent(UUID.randomUUID(), "private@rumbo.test",
        "secret", Instant.now(), Instant.now()))).doesNotThrowAnyException();
  }
}
