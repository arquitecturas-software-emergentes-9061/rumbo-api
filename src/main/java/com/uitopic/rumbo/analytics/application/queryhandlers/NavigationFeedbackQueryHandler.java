package com.uitopic.rumbo.analytics.application.queryhandlers;

import com.uitopic.rumbo.analytics.application.ports.NavigationInsightsReader;
import com.uitopic.rumbo.analytics.domain.exceptions.SessionFeedbackNotFoundException;
import com.uitopic.rumbo.analytics.domain.model.queries.FeedbackQueries.GetFeedbackSummaryQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.FeedbackQueries.GetSessionFeedbackQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.FeedbackQueries.ListIncidentReportsQuery;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackSummary;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentRecord;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SessionFeedback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feedback of the visitors for the dashboard of the site (WI-TS31-06). */
@Service
public class NavigationFeedbackQueryHandler {
  private final NavigationInsightsReader reader;

  public NavigationFeedbackQueryHandler(NavigationInsightsReader reader) {
    this.reader = reader;
  }

  @Transactional(readOnly = true)
  public FeedbackSummary handle(GetFeedbackSummaryQuery query) {
    return reader.feedbackSummary(query.siteId(), query.period());
  }

  @Transactional(readOnly = true)
  public IncidentRecord.Page handle(ListIncidentReportsQuery query) {
    return reader.incidents(query.siteId(), query.period(), query.category(), query.page(), query.size());
  }

  @Transactional(readOnly = true)
  public SessionFeedback handle(GetSessionFeedbackQuery query) {
    return reader.sessionFeedback(query.siteId(), query.navigationSessionId())
      .orElseThrow(SessionFeedbackNotFoundException::new);
  }
}
