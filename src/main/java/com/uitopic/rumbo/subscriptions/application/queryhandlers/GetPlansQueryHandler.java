package com.uitopic.rumbo.subscriptions.application.queryhandlers;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Plan;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetPlansQuery;
import com.uitopic.rumbo.subscriptions.domain.repositories.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;

@Service
public class GetPlansQueryHandler {
  private final PlanRepository repository;

  public GetPlansQueryHandler(PlanRepository repository) { this.repository = repository; }

  @Transactional(readOnly = true)
  public List<Plan> handle(GetPlansQuery query) {
    return repository.findAllActive().stream().filter(Plan::active)
      .sorted(Comparator.comparingInt(Plan::displayOrder).thenComparing(Plan::code)).toList();
  }
}
