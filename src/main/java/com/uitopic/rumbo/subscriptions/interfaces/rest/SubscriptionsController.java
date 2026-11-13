package com.uitopic.rumbo.subscriptions.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.subscriptions.application.ports.PlanQuotaCatalog;
import com.uitopic.rumbo.subscriptions.application.queryhandlers.GetMySubscriptionQueryHandler;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetMySubscriptionQuery;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.MySubscriptionResponse;
import com.uitopic.rumbo.subscriptions.interfaces.rest.transform.MySubscriptionResourceAssembler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class SubscriptionsController {
  private final GetMySubscriptionQueryHandler handler;

  private final PlanQuotaCatalog quotas;

  public SubscriptionsController(GetMySubscriptionQueryHandler handler, PlanQuotaCatalog quotas) {
    this.handler = handler;
    this.quotas = quotas;
  }

  @GetMapping("/me")
  public MySubscriptionResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    var view = handler.handle(new GetMySubscriptionQuery(principal.userId()));
    return MySubscriptionResourceAssembler.toResource(view, quotas.quotaFor(view.plan().code()));
  }
}
