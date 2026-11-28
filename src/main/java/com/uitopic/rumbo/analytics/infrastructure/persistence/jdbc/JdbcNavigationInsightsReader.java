package com.uitopic.rumbo.analytics.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.analytics.application.ports.NavigationInsightsReader;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackSummary;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackSummary.CategoryCount;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackSummary.ScoreCount;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentRecord;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentZones;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationTrend;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SessionFeedback;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SiteActivity;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.TrendGranularity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Dashboard read model in PostgreSQL: feedback facts (TS-31) and navigation events (TS-30) aggregated by site and
 * period. Periods are half-open, {@code [from, to)}, like every other analytics query.
 */
@Repository
public class JdbcNavigationInsightsReader implements NavigationInsightsReader {
  private static final String PERIOD_FACTS = """
    FROM analytics_feedback_facts
    WHERE site_id = :siteId AND occurred_at >= :from AND occurred_at < :to
    """;
  private static final String FEEDBACK_TOTALS = """
    SELECT count(*) FILTER (WHERE kind = 'RATING') AS ratings,
      avg(score) FILTER (WHERE kind = 'RATING') AS average_score,
      count(*) FILTER (WHERE kind = 'INCIDENT') AS incidents,
      count(*) FILTER (WHERE kind = 'INCIDENT' AND position_x IS NULL) AS without_position
    """ + PERIOD_FACTS;
  private static final String SCORES = "SELECT score, count(*) AS ratings " + PERIOD_FACTS
    + " AND kind = 'RATING' GROUP BY score";
  private static final String CATEGORIES = "SELECT category, count(*) AS incidents " + PERIOD_FACTS
    + " AND kind = 'INCIDENT' GROUP BY category ORDER BY incidents DESC, category";
  private static final String INCIDENT_COLUMNS = """
    SELECT source_id, navigation_session_id, floor_id, node_reference, category, position_x, position_y, occurred_at
    """;
  private static final String INCIDENT_FILTER = PERIOD_FACTS
    + " AND kind = 'INCIDENT' AND (CAST(:category AS varchar) IS NULL OR category = CAST(:category AS varchar))";
  private static final String SESSION_FACTS = """
    SELECT source_id, kind, navigation_session_id, floor_id, node_reference, score, category, position_x, position_y,
      occurred_at
    FROM analytics_feedback_facts
    WHERE site_id = :siteId AND navigation_session_id = :sessionId
    ORDER BY occurred_at, source_id
    """;
  private static final String TREND = """
    WITH bounds AS (
      SELECT CAST(:from AS timestamptz) AS from_at, CAST(:to AS timestamptz) AS to_at
    ), buckets AS (
      SELECT generate_series(date_trunc(:unit, from_at AT TIME ZONE :zone),
        date_trunc(:unit, (to_at - interval '1 microsecond') AT TIME ZONE :zone),
        CAST('1 ' || :unit AS interval)) AS local_start
      FROM bounds
    ), events AS (
      SELECT date_trunc(:unit, e.occurred_at AT TIME ZONE :zone) AS local_start, e.navigation_session_id,
        e.event_type, e.metadata
      FROM navigation_events e, bounds
      WHERE e.site_id = :siteId AND e.occurred_at >= bounds.from_at AND e.occurred_at < bounds.to_at
    ), event_counts AS (
      SELECT local_start,
        count(DISTINCT navigation_session_id) FILTER (WHERE event_type = 'NAVIGATION_STARTED') AS started,
        count(DISTINCT navigation_session_id) FILTER (WHERE event_type = 'NAVIGATION_FINISHED') AS finished,
        count(DISTINCT navigation_session_id) FILTER (WHERE event_type = 'NAVIGATION_FINISHED'
          AND metadata ->> 'result' = 'ARRIVED') AS arrived,
        count(*) FILTER (WHERE event_type = 'DEVIATION_DETECTED') AS deviations,
        count(*) FILTER (WHERE event_type = 'ROUTE_RECALCULATED') AS recalculations
      FROM events GROUP BY local_start
    ), fact_counts AS (
      SELECT date_trunc(:unit, f.occurred_at AT TIME ZONE :zone) AS local_start,
        count(*) FILTER (WHERE f.kind = 'INCIDENT') AS incidents,
        count(*) FILTER (WHERE f.kind = 'RATING') AS ratings,
        avg(f.score) FILTER (WHERE f.kind = 'RATING') AS average_score
      FROM analytics_feedback_facts f, bounds
      WHERE f.site_id = :siteId AND f.occurred_at >= bounds.from_at AND f.occurred_at < bounds.to_at
      GROUP BY 1
    )
    SELECT b.local_start AT TIME ZONE :zone AS period_start,
      coalesce(e.started, 0) AS started, coalesce(e.finished, 0) AS finished, coalesce(e.arrived, 0) AS arrived,
      coalesce(e.deviations, 0) AS deviations, coalesce(e.recalculations, 0) AS recalculations,
      coalesce(f.incidents, 0) AS incidents, coalesce(f.ratings, 0) AS ratings, f.average_score
    FROM buckets b
    LEFT JOIN event_counts e USING (local_start)
    LEFT JOIN fact_counts f USING (local_start)
    ORDER BY b.local_start
    """;
  private static final String FLOOR_FILTER = " AND (CAST(:floorId AS uuid) IS NULL OR floor_id = CAST(:floorId AS uuid))";
  private static final String ZONES = """
    WITH deviations AS (
      SELECT floor_id, node_reference,
        count(*) FILTER (WHERE event_type = 'DEVIATION_DETECTED') AS deviations,
        count(*) FILTER (WHERE event_type = 'ROUTE_RECALCULATED') AS recalculations
      FROM navigation_events
      WHERE site_id = :siteId AND occurred_at >= :from AND occurred_at < :to
        AND event_type IN ('DEVIATION_DETECTED', 'ROUTE_RECALCULATED')
        AND floor_id IS NOT NULL AND node_reference IS NOT NULL""" + FLOOR_FILTER + """

      GROUP BY floor_id, node_reference
    ), incidents AS (
      SELECT floor_id, node_reference, count(*) AS incidents,
        mode() WITHIN GROUP (ORDER BY category) AS top_category
      """ + PERIOD_FACTS + """
        AND kind = 'INCIDENT' AND floor_id IS NOT NULL AND node_reference IS NOT NULL""" + FLOOR_FILTER + """

      GROUP BY floor_id, node_reference
    )
    SELECT coalesce(d.floor_id, i.floor_id) AS floor_id, coalesce(d.node_reference, i.node_reference) AS node_reference,
      coalesce(d.deviations, 0) AS deviations, coalesce(d.recalculations, 0) AS recalculations,
      coalesce(i.incidents, 0) AS incidents, i.top_category
    FROM deviations d FULL JOIN incidents i ON d.floor_id = i.floor_id AND d.node_reference = i.node_reference
    ORDER BY coalesce(d.deviations, 0) + coalesce(i.incidents, 0) DESC, 1, 2
    LIMIT :limit
    """;
  private static final String HOTSPOTS = """
    WITH cell AS (SELECT CAST(:cellSize AS double precision) AS size), points AS (
      SELECT f.floor_id, floor(f.position_x / cell.size) AS gx, floor(f.position_y / cell.size) AS gy, cell.size
      FROM analytics_feedback_facts f, cell
      WHERE f.site_id = :siteId AND f.occurred_at >= :from AND f.occurred_at < :to AND f.kind = 'INCIDENT'
        AND f.position_x IS NOT NULL AND (CAST(:floorId AS uuid) IS NULL OR f.floor_id = CAST(:floorId AS uuid))
    )
    SELECT floor_id, (gx + 0.5) * size AS x, (gy + 0.5) * size AS y, count(*) AS incidents
    FROM points GROUP BY floor_id, gx, gy, size
    ORDER BY incidents DESC, floor_id, gx, gy
    LIMIT :limit
    """;
  private static final String UNLOCATED = "SELECT count(*) " + PERIOD_FACTS
    + " AND kind = 'INCIDENT' AND position_x IS NULL AND node_reference IS NULL"
    + " AND (CAST(:floorId AS uuid) IS NULL OR floor_id = CAST(:floorId AS uuid) OR floor_id IS NULL)";
  private static final String ACTIVITY = """
    WITH events AS (
      SELECT site_id,
        count(DISTINCT navigation_session_id) FILTER (WHERE event_type = 'NAVIGATION_STARTED') AS started,
        count(DISTINCT navigation_session_id) FILTER (WHERE event_type = 'NAVIGATION_FINISHED') AS finished,
        count(DISTINCT navigation_session_id) FILTER (WHERE event_type = 'NAVIGATION_FINISHED'
          AND metadata ->> 'result' = 'ARRIVED') AS arrived
      FROM navigation_events
      WHERE site_id IN (:siteIds) AND occurred_at >= :from AND occurred_at < :to
      GROUP BY site_id
    ), facts AS (
      SELECT site_id, count(*) FILTER (WHERE kind = 'INCIDENT') AS incidents,
        count(*) FILTER (WHERE kind = 'RATING') AS ratings, avg(score) FILTER (WHERE kind = 'RATING') AS average_score
      FROM analytics_feedback_facts
      WHERE site_id IN (:siteIds) AND occurred_at >= :from AND occurred_at < :to
      GROUP BY site_id
    )
    SELECT coalesce(e.site_id, f.site_id) AS site_id, coalesce(e.started, 0) AS started,
      coalesce(e.finished, 0) AS finished, coalesce(e.arrived, 0) AS arrived, coalesce(f.incidents, 0) AS incidents,
      coalesce(f.ratings, 0) AS ratings, f.average_score
    FROM events e FULL JOIN facts f ON e.site_id = f.site_id
    """;

  private static final RowMapper<IncidentRecord> INCIDENT = (rs, row) -> new IncidentRecord(
    rs.getObject("source_id", UUID.class), rs.getObject("navigation_session_id", UUID.class),
    rs.getObject("floor_id", UUID.class), rs.getString("node_reference"),
    IncidentCategory.valueOf(rs.getString("category")), nullableDouble(rs, "position_x"),
    nullableDouble(rs, "position_y"), rs.getTimestamp("occurred_at").toInstant());

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcNavigationInsightsReader(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public FeedbackSummary feedbackSummary(UUID siteId, DateRange period) {
    var parameters = period(siteId, period);
    var totals = jdbc.queryForMap(FEEDBACK_TOTALS, parameters);
    Map<Integer, Long> byScore = new HashMap<>();
    jdbc.query(SCORES, parameters, rs -> {
      byScore.put(rs.getInt("score"), rs.getLong("ratings"));
    });
    List<ScoreCount> distribution = new ArrayList<>();
    for (int score = 1; score <= 5; score++) distribution.add(new ScoreCount(score, byScore.getOrDefault(score, 0L)));
    List<CategoryCount> categories = jdbc.query(CATEGORIES, parameters, (rs, row) ->
      new CategoryCount(IncidentCategory.valueOf(rs.getString("category")), rs.getLong("incidents")));
    Number average = (Number) totals.get("average_score");
    return new FeedbackSummary(((Number) totals.get("ratings")).longValue(),
      average == null ? null : average.doubleValue(), distribution, ((Number) totals.get("incidents")).longValue(),
      ((Number) totals.get("without_position")).longValue(), categories);
  }

  @Override
  public IncidentRecord.Page incidents(UUID siteId, DateRange period, IncidentCategory category, int page, int size) {
    var parameters = period(siteId, period)
      .addValue("category", category == null ? null : category.name(), Types.VARCHAR)
      .addValue("limit", size)
      .addValue("offset", (long) page * size);
    Long total = jdbc.queryForObject("SELECT count(*) " + INCIDENT_FILTER, parameters, Long.class);
    List<IncidentRecord> items = jdbc.query(INCIDENT_COLUMNS + INCIDENT_FILTER
      + " ORDER BY occurred_at DESC, source_id DESC LIMIT :limit OFFSET :offset", parameters, INCIDENT);
    return new IncidentRecord.Page(items, total == null ? 0 : total, page, size);
  }

  @Override
  public Optional<SessionFeedback> sessionFeedback(UUID siteId, UUID navigationSessionId) {
    var parameters = new MapSqlParameterSource("siteId", siteId).addValue("sessionId", navigationSessionId);
    List<SessionFeedback.Rating> ratings = new ArrayList<>();
    List<IncidentRecord> incidents = new ArrayList<>();
    jdbc.query(SESSION_FACTS, parameters, rs -> {
      if ("RATING".equals(rs.getString("kind"))) {
        ratings.add(new SessionFeedback.Rating(rs.getObject("source_id", UUID.class), rs.getInt("score"),
          rs.getString("node_reference"), rs.getTimestamp("occurred_at").toInstant()));
      } else {
        incidents.add(INCIDENT.mapRow(rs, incidents.size()));
      }
    });
    if (ratings.isEmpty() && incidents.isEmpty()) return Optional.empty();
    return Optional.of(new SessionFeedback(navigationSessionId, siteId, ratings.isEmpty() ? null : ratings.get(0),
      incidents));
  }

  @Override
  public NavigationTrend trend(UUID siteId, DateRange period, TrendGranularity granularity, ZoneId zone) {
    var parameters = new MapSqlParameterSource("siteId", siteId)
      .addValue("from", OffsetDateTime.ofInstant(period.from(), ZoneOffset.UTC))
      .addValue("to", OffsetDateTime.ofInstant(period.to(), ZoneOffset.UTC))
      .addValue("unit", granularity.sqlUnit())
      .addValue("zone", zone.getId());
    List<NavigationTrend.Point> points = jdbc.query(TREND, parameters, (rs, row) -> new NavigationTrend.Point(
      rs.getObject("period_start", OffsetDateTime.class).toInstant(), rs.getLong("started"), rs.getLong("finished"),
      rs.getLong("arrived"), rs.getLong("deviations"), rs.getLong("recalculations"), rs.getLong("incidents"),
      rs.getLong("ratings"), nullableDouble(rs, "average_score")));
    return new NavigationTrend(granularity, zone.getId(), points);
  }

  @Override
  public IncidentZones incidentZones(UUID siteId, DateRange period, UUID floorId, double cellSize) {
    var parameters = period(siteId, period).addValue("floorId", floorId, Types.OTHER)
      .addValue("cellSize", cellSize);
    List<IncidentZones.Zone> zones = jdbc.query(ZONES, parameters.addValue("limit", MAX_ZONES), (rs, row) -> {
      String top = rs.getString("top_category");
      return new IncidentZones.Zone(rs.getObject("floor_id", UUID.class), rs.getString("node_reference"),
        rs.getLong("deviations"), rs.getLong("recalculations"), rs.getLong("incidents"),
        top == null ? null : IncidentCategory.valueOf(top));
    });
    List<IncidentZones.Hotspot> hotspots = jdbc.query(HOTSPOTS, parameters.addValue("limit", MAX_HOTSPOTS),
      (rs, row) -> new IncidentZones.Hotspot(rs.getObject("floor_id", UUID.class), rs.getDouble("x"),
        rs.getDouble("y"), rs.getLong("incidents")));
    Long unlocated = jdbc.queryForObject(UNLOCATED, parameters, Long.class);
    return new IncidentZones(zones, cellSize, hotspots, unlocated == null ? 0 : unlocated);
  }

  @Override
  public List<SiteActivity> activity(Collection<UUID> siteIds, DateRange period) {
    if (siteIds.isEmpty()) return List.of();
    var parameters = new MapSqlParameterSource("siteIds", List.copyOf(siteIds))
      .addValue("from", Timestamp.from(period.from()))
      .addValue("to", Timestamp.from(period.to()));
    return jdbc.query(ACTIVITY, parameters, (rs, row) -> {
      long started = rs.getLong("started");
      return new SiteActivity(rs.getObject("site_id", UUID.class), null, started, rs.getLong("finished"),
        started == 0 ? 0 : (double) rs.getLong("arrived") / started, rs.getLong("incidents"), rs.getLong("ratings"),
        nullableDouble(rs, "average_score"));
    });
  }

  private static MapSqlParameterSource period(UUID siteId, DateRange period) {
    return new MapSqlParameterSource("siteId", siteId)
      .addValue("from", Timestamp.from(period.from()))
      .addValue("to", Timestamp.from(period.to()));
  }

  private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? null : value;
  }
}
