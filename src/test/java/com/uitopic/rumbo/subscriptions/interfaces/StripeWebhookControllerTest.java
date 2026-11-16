package com.uitopic.rumbo.subscriptions.interfaces;
import com.uitopic.rumbo.subscriptions.interfaces.rest.*;
import com.uitopic.rumbo.subscriptions.infrastructure.payments.PaymentEventVerifierConfiguration;
import com.uitopic.rumbo.subscriptions.application.commandhandlers.ConfirmPaymentCommandHandler;
import com.uitopic.rumbo.subscriptions.support.StripeWebhookFixture;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class StripeWebhookControllerTest {
  @Test void absentSecretReturns503WithoutProcessing() throws Exception {
    var handler = mock(ConfirmPaymentCommandHandler.class);
    var verifier = new PaymentEventVerifierConfiguration().paymentEventVerifier("");
    var mvc = MockMvcBuilders.standaloneSetup(new StripeWebhookController(verifier, handler))
        .setControllerAdvice(new SubscriptionsExceptionHandler()).build();
    mvc.perform(post("/api/v1/subscriptions/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PAYMENTS_UNAVAILABLE"));
    verifyNoInteractions(handler);
  }
  @Test void invalidSignatureReturns400WithoutProcessing() throws Exception {
    var handler = mock(ConfirmPaymentCommandHandler.class);
    var verifier = new PaymentEventVerifierConfiguration().paymentEventVerifier(StripeWebhookFixture.randomSecret());
    var mvc = MockMvcBuilders.standaloneSetup(new StripeWebhookController(verifier, handler))
        .setControllerAdvice(new SubscriptionsExceptionHandler()).build();
    mvc.perform(post("/api/v1/subscriptions/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    verifyNoInteractions(handler);
  }
}
