package com.uitopic.rumbo.analytics.application.queryhandlers;

import com.uitopic.rumbo.analytics.application.ports.AdministeredSites;
import com.uitopic.rumbo.analytics.application.ports.NavigationInsightsReader;
import com.uitopic.rumbo.analytics.domain.exceptions.MetricsUnavailableException;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries.GetIncidentZonesQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries.GetNavigationTrendQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries.GetSitesOverviewQuery;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentZones;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationTrend;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SiteActivity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trends, incident zones and the comparison between sites of an administrator (WI-US25-07, WI-US25-08). */
@Service
public class NavigationInsightsQueryHandler {
  private final NavigationInsightsReader reader;
  private final ObjectProvider<AdministeredSites> administeredSites;

  public NavigationInsightsQueryHandler(NavigationInsightsReader reader,
                                        ObjectProvider<AdministeredSites> administeredSites) {
    this.reader = reader;
    this.administeredSites = administeredSites;
  }

  @Transactional(readOnly = true)
  public NavigationTrend handle(GetNavigationTrendQuery query) {
    return reader.trend(query.siteId(), query.period(), query.granularity(), query.zone());
  }

  @Transactional(readOnly = true)
  public IncidentZones handle(GetIncidentZonesQuery query) {
    return reader.incidentZones(query.siteId(), query.period(), query.floorId(), query.cellSize());
  }

  /**
   * Every site of the administrator in the order Planning lists them, with zeros for a site without activity. Only
   * sites of the caller are read, whatever ids reach the read model.
   */
  @Transactional(readOnly = true)
  public List<SiteActivity> handle(GetSitesOverviewQuery query) {
    AdministeredSites sites = administeredSites.getIfAvailable();
    if (sites == null) throw new MetricsUnavailableException("The sites of the administrator are not available");
    List<AdministeredSites.Site> owned = sites.of(query.administratorUserId());
    if (owned.isEmpty()) return List.of();
    Map<UUID, SiteActivity> activity = reader.activity(owned.stream().map(AdministeredSites.Site::id).toList(),
        query.period()).stream()
      .collect(Collectors.toMap(SiteActivity::siteId, Function.identity()));
    return owned.stream()
      .map(site -> activity.containsKey(site.id()) ? activity.get(site.id()).named(site.name())
        : SiteActivity.empty(site.id(), site.name()))
      .toList();
  }
}
