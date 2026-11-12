package com.uitopic.rumbo.analytics.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.analytics.application.ports.NavigationMetricsReader;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DestinationUsage;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** Consolidated site indicators calculated in PostgreSQL from sessions started in the requested period. */
@Repository
public class JdbcNavigationMetricsReader implements NavigationMetricsReader {
  private static final String PERIOD_EVENTS = """
    WITH period_events AS (
      SELECT * FROM navigation_events
      WHERE site_id = :siteId AND occurred_at >= :from AND occurred_at < :to
    ), starts AS (
      SELECT DISTINCT ON (navigation_session_id) navigation_session_id, metadata
      FROM period_events WHERE event_type = 'NAVIGATION_STARTED'
      ORDER BY navigation_session_id, occurred_at, id
    )
    """;
  private static final String COUNTERS = PERIOD_EVENTS + """
    , session_events AS (
      SELECT e.* FROM period_events e JOIN starts s USING (navigation_session_id)
    ), finishes AS (
      SELECT DISTINCT ON (navigation_session_id) navigation_session_id, metadata
      FROM session_events WHERE event_type = 'NAVIGATION_FINISHED'
      ORDER BY navigation_session_id, occurred_at DESC, id DESC
    ), durations AS (
      SELECT CASE WHEN jsonb_typeof(metadata -> 'durationSeconds') = 'number'
        THEN (metadata ->> 'durationSeconds')::numeric END AS seconds
      FROM finishes
    )
    SELECT (SELECT count(*) FROM starts) AS started,
      (SELECT count(*) FROM finishes) AS finished,
      (SELECT count(DISTINCT navigation_session_id) FROM session_events
        WHERE event_type = 'NAVIGATION_FINISHED' AND metadata ->> 'result' = 'ARRIVED') AS arrived,
      (SELECT coalesce(avg(seconds), 0) FROM durations WHERE seconds >= 0) AS duration,
      (SELECT count(*) FROM session_events WHERE event_type = 'DEVIATION_DETECTED') AS deviations,
      (SELECT count(DISTINCT navigation_session_id) FROM session_events
        WHERE event_type = 'ROUTE_RECALCULATED') AS recalculated
    """;
  private static final String DESTINATIONS = PERIOD_EVENTS + """
    SELECT metadata ->> 'destinationNodeReference' AS destination, count(*) AS sessions
    FROM starts WHERE metadata ->> 'destinationNodeReference' IS NOT NULL
    GROUP BY metadata ->> 'destinationNodeReference'
    ORDER BY sessions DESC, destination ASC
    LIMIT :limit
    """;

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcNavigationMetricsReader(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public NavigationMetrics summarize(UUID siteId, DateRange period) {
    var parameters = new MapSqlParameterSource("siteId", siteId)
      .addValue("from", Timestamp.from(period.from()))
      .addValue("to", Timestamp.from(period.to()))
      .addValue("limit", TOP_DESTINATIONS);
    NavigationMetrics counters = jdbc.queryForObject(COUNTERS, parameters, (rs, row) -> {
      long started = rs.getLong("started");
      if (started == 0) return NavigationMetrics.EMPTY;
      return new NavigationMetrics(started, rs.getLong("finished"), rs.getDouble("arrived") / started,
        rs.getDouble("duration"), rs.getDouble("deviations") / started,
        rs.getDouble("recalculated") / started, List.of());
    });
    if (counters.startedSessions() == 0) return NavigationMetrics.EMPTY;
    var destinations = jdbc.query(DESTINATIONS, parameters,
      (rs, row) -> new DestinationUsage(rs.getString("destination"), rs.getLong("sessions")));
    return new NavigationMetrics(counters.startedSessions(), counters.finishedSessions(), counters.completionRate(),
      counters.averageDurationSeconds(), counters.averageDeviationsPerSession(), counters.recalculationRate(),
      destinations);
  }
}
