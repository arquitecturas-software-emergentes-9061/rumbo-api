package com.uitopic.rumbo.subscriptions.application;

import com.uitopic.rumbo.subscriptions.application.queryhandlers.GetPlansQueryHandler;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Plan;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetPlansQuery;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import com.uitopic.rumbo.subscriptions.domain.repositories.PlanRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetPlansQueryHandlerTest {
  private final PlanRepository repository = mock(PlanRepository.class);
  private final GetPlansQueryHandler handler = new GetPlansQueryHandler(repository);

  @Test
  void filtersInactivePlansAndSortsByDisplayOrder() {
    Plan first = plan(PlanCode.BASIC, 1, true);
    Plan last = plan(PlanCode.ENTERPRISE, 3, true);
    when(repository.findAllActive()).thenReturn(List.of(last, plan(PlanCode.PROFESSIONAL, 2, false), first));
    assertThat(handler.handle(new GetPlansQuery())).containsExactly(first, last);
  }

  @Test
  void returnsAnEmptyCatalogWhenThereAreNoActivePlans() {
    when(repository.findAllActive()).thenReturn(List.of());
    assertThat(handler.handle(new GetPlansQuery())).isEmpty();
  }

  private Plan plan(PlanCode code, int order, boolean active) {
    return new Plan(UUID.randomUUID(), code, code.name(), new Money(BigDecimal.ONE, "PEN"),
      BillingInterval.MONTH, PurchaseMode.CHECKOUT, order, active);
  }
}
