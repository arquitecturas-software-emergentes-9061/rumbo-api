package com.uitopic.rumbo.analytics.interfaces.rest;

import com.uitopic.rumbo.analytics.application.queryhandlers.NavigationFeedbackQueryHandler;
import com.uitopic.rumbo.analytics.application.queryhandlers.NavigationInsightsQueryHandler;
import com.uitopic.rumbo.analytics.domain.model.queries.FeedbackQueries;
import com.uitopic.rumbo.analytics.domain.model.queries.FeedbackQueries.GetFeedbackSummaryQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.FeedbackQueries.GetSessionFeedbackQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.FeedbackQueries.ListIncidentReportsQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries.GetIncidentZonesQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries.GetNavigationTrendQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries.GetSitesOverviewQuery;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackSummary;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentRecord;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentZones;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationTrend;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SessionFeedback;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SiteActivity;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.TrendGranularity;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feedback of the visitors (TS-31) and navigation reports (US-25) for the panel of the clinic administrator. Every
 * read of a site requires owning it; the overview lists only the caller's sites.
 */
@RestController
@RequestMapping(value = "/api/v1/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics", description = "Navigation telemetry and dashboard metrics")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class NavigationInsightsController {
  private static final String SITE_ADMIN = "hasRole('CLINIC_ADMIN') and @siteOwnerGuard.isOwner(authentication, #siteId)";

  private final NavigationFeedbackQueryHandler feedback;
  private final NavigationInsightsQueryHandler insights;

  public NavigationInsightsController(NavigationFeedbackQueryHandler feedback, NavigationInsightsQueryHandler insights) {
    this.feedback = feedback;
    this.insights = insights;
  }

  @GetMapping("/feedback-summary")
  @PreAuthorize(SITE_ADMIN)
  @Operation(summary = "Ratings and incident reports of a site in a period (TS-31)")
  public FeedbackSummary feedbackSummary(@RequestParam UUID siteId,
                                         @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
                                         @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {
    return feedback.handle(new GetFeedbackSummaryQuery(siteId, new DateRange(from, to)));
  }

  @GetMapping("/incident-reports")
  @PreAuthorize(SITE_ADMIN)
  @Operation(summary = "Incident reports of a site in a period, newest first, optionally of one category (TS-31)")
  public IncidentRecord.Page incidentReports(@RequestParam UUID siteId,
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) IncidentCategory category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + FeedbackQueries.DEFAULT_PAGE_SIZE) int size) {
    return feedback.handle(new ListIncidentReportsQuery(siteId, new DateRange(from, to), category, page, size));
  }

  @GetMapping("/navigation-sessions/{sessionId}/feedback")
  @PreAuthorize(SITE_ADMIN)
  @Operation(summary = "Rating and incident reports of one navigation session of a site (TS-31)")
  public SessionFeedback sessionFeedback(@PathVariable UUID sessionId, @RequestParam UUID siteId) {
    return feedback.handle(new GetSessionFeedbackQuery(siteId, sessionId));
  }

  @GetMapping("/navigation-trends")
  @PreAuthorize(SITE_ADMIN)
  @Operation(summary = "Indicators of a site per day or week, with every point of the period (US-25)")
  public NavigationTrend navigationTrends(@RequestParam UUID siteId,
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
      @RequestParam(defaultValue = "DAY") TrendGranularity granularity,
      @RequestParam(defaultValue = InsightQueries.DEFAULT_ZONE) String zone) {
    return insights.handle(new GetNavigationTrendQuery(siteId, new DateRange(from, to), granularity,
      GetNavigationTrendQuery.zone(zone)));
  }

  @GetMapping("/incident-zones")
  @PreAuthorize(SITE_ADMIN)
  @Operation(summary = "Nodes with deviations or reports and positioned reports grouped in cells, for the incident "
    + "map (US-25)")
  public IncidentZones incidentZones(@RequestParam UUID siteId,
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) UUID floorId,
      @RequestParam(defaultValue = "" + InsightQueries.DEFAULT_CELL_SIZE) double cellSize) {
    return insights.handle(new GetIncidentZonesQuery(siteId, new DateRange(from, to), floorId, cellSize));
  }

  @GetMapping("/sites-overview")
  @PreAuthorize("hasRole('CLINIC_ADMIN')")
  @Operation(summary = "Indicators of every site of the administrator in a period, to compare them (US-25)")
  public List<SiteActivity> sitesOverview(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {
    return insights.handle(new GetSitesOverviewQuery(principal.userId(), new DateRange(from, to)));
  }
}
