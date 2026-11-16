package com.uitopic.rumbo.subscriptions.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.subscriptions.application.commandhandlers.CreateCheckoutSessionCommandHandler;
import com.uitopic.rumbo.subscriptions.application.queryhandlers.GetPaymentOrderQueryHandler;
import com.uitopic.rumbo.subscriptions.domain.model.commands.CreateCheckoutSessionCommand;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetPaymentOrderQuery;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.*;
import com.uitopic.rumbo.subscriptions.interfaces.rest.transform.PaymentOrderResourceAssembler;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class PaymentOrdersController {
  private final CreateCheckoutSessionCommandHandler create;
  private final GetPaymentOrderQueryHandler get;
  public PaymentOrdersController(CreateCheckoutSessionCommandHandler create, GetPaymentOrderQueryHandler get) {
    this.create = create; this.get = get;
  }
  @PostMapping("/checkout-sessions")
  public ResponseEntity<CheckoutSessionResponse> create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody CreateCheckoutSessionRequest request) {
    var result = create.handle(new CreateCheckoutSessionCommand(principal.userId(), request.planCode(), request.billing().toDomain()));
    return ResponseEntity.created(URI.create("/api/v1/subscriptions/payment-orders/" + result.order().id()))
      .body(PaymentOrderResourceAssembler.toResource(result));
  }
  @GetMapping("/payment-orders/{orderId}")
  public PaymentOrderResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID orderId) {
    return PaymentOrderResourceAssembler.toResource(get.handle(new GetPaymentOrderQuery(orderId, principal.userId())));
  }
}
