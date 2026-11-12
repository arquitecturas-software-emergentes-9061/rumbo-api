package com.uitopic.rumbo.subscriptions.application;

import com.uitopic.rumbo.subscriptions.application.commandhandlers.CreateCheckoutSessionCommandHandler;
import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import com.uitopic.rumbo.subscriptions.application.exceptions.*;
import com.uitopic.rumbo.subscriptions.domain.exceptions.*;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.*;
import com.uitopic.rumbo.subscriptions.domain.model.commands.CreateCheckoutSessionCommand;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import com.uitopic.rumbo.subscriptions.domain.repositories.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CreateCheckoutSessionCommandHandlerTest {
  PlanRepository plans = mock(PlanRepository.class);
  PaymentOrderRepository orders = mock(PaymentOrderRepository.class);
  com.uitopic.rumbo.subscriptions.application.ports.OrganizationDirectory organizations = mock(com.uitopic.rumbo.subscriptions.application.ports.OrganizationDirectory.class);
  final OrganizationRef organization = new OrganizationRef(UUID.randomUUID(), "Clinic");
  PaymentGateway gateway = mock(PaymentGateway.class);
  PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
  CreateCheckoutSessionCommandHandler handler;
  List<String> events = new ArrayList<>();
  final UUID payer = UUID.randomUUID();
  @BeforeEach void setup() {
    when(manager.getTransaction(any())).thenAnswer(i -> { events.add("begin"); return new SimpleTransactionStatus(); });
    doAnswer(i -> { events.add("commit"); return null; }).when(manager).commit(any());
    when(orders.save(any())).thenAnswer(i -> { PaymentOrder o = i.getArgument(0); events.add(o.status().name()); return o; });
    when(plans.findByCode(any())).thenReturn(Optional.of(plan(PlanCode.BASIC, true, PurchaseMode.CHECKOUT)));
    when(organizations.findOrganizationOf(payer)).thenReturn(Optional.of(organization));
    handler = new CreateCheckoutSessionCommandHandler(plans, orders, gateway, organizations, manager);
  }
  private Plan plan(PlanCode code, boolean active, PurchaseMode mode) {
    return new Plan(UUID.randomUUID(), code, "Plan", new Money(new BigDecimal("39.90"), "PEN"), BillingInterval.MONTH, mode, 1, active);
  }
  private CreateCheckoutSessionCommand command() { return new CreateCheckoutSessionCommand(payer, PlanCode.BASIC); }
  @Test void commitsPendingBeforeCallingGatewayThenStoresSession() {
    when(gateway.createCheckoutSession(any())).thenAnswer(i -> {
      assertThat(events).containsExactly("begin", "PENDING", "commit");
      var request = (PaymentGateway.CheckoutRequest)i.getArgument(0);
      assertThat(request.payerUserId()).isEqualTo(payer);
      assertThat(request.amount().amount()).isEqualTo(new BigDecimal("39.90"));
      events.add("gateway");
      return new PaymentGateway.CheckoutSession("session", "https://checkout.stripe.com/test");
    });
    var result = handler.handle(command());
    assertThat(result.order().organization()).isEqualTo(organization);
    assertThat(result.order().providerSessionId()).isEqualTo("session");
    assertThat(result.order().status()).isEqualTo(PaymentOrderStatus.PENDING);
    assertThat(events).containsExactly("begin", "PENDING", "commit", "gateway", "begin", "PENDING", "commit");
  }
  @Test void enterpriseRequiresSalesAndDoesNotCreateOrder() {
    when(plans.findByCode(any())).thenReturn(Optional.of(plan(PlanCode.ENTERPRISE, true, PurchaseMode.CONTACT_SALES)));
    assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(PlanRequiresSalesContactException.class);
    verifyNoInteractions(gateway, orders, organizations);
  }
  @Test void inactiveAndMissingPlansAreNotFound() {
    when(plans.findByCode(any())).thenReturn(Optional.of(plan(PlanCode.BASIC, false, PurchaseMode.CHECKOUT)), Optional.empty());
    assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(PlanNotFoundException.class);
    assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(PlanNotFoundException.class);
    verifyNoInteractions(gateway, orders, organizations);
  }
  @Test void missingOrganizationDoesNotSaveOrCallGateway() {
    when(organizations.findOrganizationOf(payer)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(OrganizationRequiredException.class);
    verifyNoInteractions(orders, gateway);
  }
  @Test void unavailableGatewayPersistsFailure() { fails(new PaymentsUnavailableException()); }
  @Test void providerErrorPersistsFailure() { fails(new PaymentProviderException()); }
  private void fails(RuntimeException error) {
    when(gateway.createCheckoutSession(any())).thenThrow(error);
    assertThatThrownBy(() -> handler.handle(command())).isSameAs(error);
    assertThat(events).containsExactly("begin", "PENDING", "commit", "begin", "FAILED", "commit");
  }
}
