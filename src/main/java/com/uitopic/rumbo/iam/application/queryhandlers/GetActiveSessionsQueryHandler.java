package com.uitopic.rumbo.iam.application.queryhandlers;

import com.uitopic.rumbo.iam.domain.model.aggregates.AuthSession;
import com.uitopic.rumbo.iam.domain.model.queries.GetActiveSessionsQuery;
import com.uitopic.rumbo.iam.domain.repositories.AuthSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class GetActiveSessionsQueryHandler {
  private final AuthSessionRepository sessions;
  private final Clock clock;

  public GetActiveSessionsQueryHandler(AuthSessionRepository sessions, Clock clock) {
    this.sessions = sessions;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AuthSession> handle(GetActiveSessionsQuery query) {
    return sessions.findActiveByUserId(query.userId(), clock.instant());
  }
}
