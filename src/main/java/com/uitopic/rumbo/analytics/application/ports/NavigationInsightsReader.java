package com.uitopic.rumbo.analytics.application.ports;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackSummary;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentRecord;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentZones;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationTrend;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SessionFeedback;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SiteActivity;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.TrendGranularity;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model of the dashboard built on the navigation events and the feedback facts (TS-31, US-25). Every read is
 * scoped to sites the caller was already authorized for.
 */
public interface NavigationInsightsReader {
  int MAX_ZONES = 200;
  int MAX_HOTSPOTS = 500;

  FeedbackSummary feedbackSummary(UUID siteId, DateRange period);

  /** Reports of the site, newest first; {@code category} {@code null} means every category. */
  IncidentRecord.Page incidents(UUID siteId, DateRange period, IncidentCategory category, int page, int size);

  /** Feedback of the session, empty if the session has no feedback on that site. */
  Optional<SessionFeedback> sessionFeedback(UUID siteId, UUID navigationSessionId);

  NavigationTrend trend(UUID siteId, DateRange period, TrendGranularity granularity, ZoneId zone);

  /** Zones of the site; {@code floorId} {@code null} means every floor. */
  IncidentZones incidentZones(UUID siteId, DateRange period, UUID floorId, double cellSize);

  /** Activity of each site, in no particular order; a site without activity may be missing. */
  List<SiteActivity> activity(Collection<UUID> siteIds, DateRange period);
}
