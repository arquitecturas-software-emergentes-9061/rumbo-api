package com.uitopic.rumbo.subscriptions.application.queryhandlers;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Plan;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetPlansQuery;
import com.uitopic.rumbo.subscriptions.domain.repositories.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import com.uitopic.rumbo.subscriptions.application.ports.PlanQuotaCatalog;

@Service
public class GetPlansQueryHandler {
  private final PlanRepository repository;
  private final PlanQuotaCatalog quotas;

  public GetPlansQueryHandler(PlanRepository repository, PlanQuotaCatalog quotas) {
    this.repository = repository;
    this.quotas = quotas;
  }

  @Transactional(readOnly = true)
  public List<PlanView> handle(GetPlansQuery query) {
    return repository.findAllActive().stream().filter(Plan::active)
      .sorted(Comparator.comparingInt(Plan::displayOrder).thenComparing(Plan::code))
      .map(plan -> new PlanView(plan, quotas.quotaFor(plan.code()))).toList();
  }
}
