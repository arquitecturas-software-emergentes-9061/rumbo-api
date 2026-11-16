package com.uitopic.rumbo.subscriptions.infrastructure;
import com.uitopic.rumbo.subscriptions.infrastructure.payments.stripe.StripePaymentEventVerifier;
import com.uitopic.rumbo.subscriptions.application.exceptions.InvalidWebhookSignatureException;
import com.uitopic.rumbo.subscriptions.support.StripeWebhookFixture;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.uitopic.rumbo.subscriptions.support.StripeWebhookFixture.*;
import static org.assertj.core.api.Assertions.*;
class StripePaymentEventVerifierTest {
  private final String secret = randomSecret();
  private final StripePaymentEventVerifier verifier = new StripePaymentEventVerifier(secret);
  private final UUID order = UUID.randomUUID();
  private final String body = payload("evt_test", "checkout.session.completed", order.toString(), true);
  @Test void verifiesSignedEventEvenWithOlderApiVersion() throws Exception {
    var e = verifier.verify(body, signature(body, secret));
    assertThat(e.id()).isEqualTo("evt_test");
    assertThat(e.type()).isEqualTo("checkout.session.completed");
    assertThat(e.orderId()).isEqualTo(order);
    assertThat(e.providerSubscriptionId()).isEqualTo("sub_test");
    assertThat(e.paid()).isTrue();
  }
  @Test void rejectsWrongSignatureAndTamperedBody() throws Exception {
    String wrong = signature(body, randomSecret());
    assertThatThrownBy(() -> verifier.verify(body, wrong)).isInstanceOf(InvalidWebhookSignatureException.class);
    String signed = signature(body, secret);
    assertThatThrownBy(() -> verifier.verify(body + " ", signed)).isInstanceOf(InvalidWebhookSignatureException.class);
  }
  @Test void rejectsExpiredSignature() throws Exception {
    String old = signature(body, secret, Instant.now().minusSeconds(600).getEpochSecond());
    assertThatThrownBy(() -> verifier.verify(body, old)).isInstanceOf(InvalidWebhookSignatureException.class);
  }
  @Test void rejectsMissingAndMalformedHeader() {
    for (String header : new String[]{null, "", "garbage"})
      assertThatThrownBy(() -> verifier.verify(body, header)).isInstanceOf(InvalidWebhookSignatureException.class);
  }
  @Test void readsMetadataFallback() throws Exception {
    String metadata = body.replace("\"client_reference_id\":\"" + order + "\"", "\"metadata\":{\"orderId\":\"" + order + "\"}");
    assertThat(verifier.verify(metadata, signature(metadata, secret)).orderId()).isEqualTo(order);
  }
  @Test void ignoresOtherEventTypesWithoutDeserializingTheirObjects() throws Exception {
    String other = "{\"id\":\"evt_other\",\"object\":\"event\",\"type\":\"invoice.paid\",\"data\":{\"object\":{}}}";
    assertThat(verifier.verify(other, signature(other, secret)).orderId()).isNull();
  }
}
