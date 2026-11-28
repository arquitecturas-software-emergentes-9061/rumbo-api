package com.uitopic.rumbo.analytics.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.analytics.domain.model.aggregates.FeedbackFact;
import com.uitopic.rumbo.analytics.domain.repositories.FeedbackFactRepository;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Stores feedback facts once: a repeated source or a second rating of a session changes nothing. */
@Repository
public class JdbcFeedbackFactRepository implements FeedbackFactRepository {
  private static final String INSERT = """
    INSERT INTO analytics_feedback_facts (source_id, kind, navigation_session_id, site_id, floor_id, node_reference,
      score, category, position_x, position_y, occurred_at, recorded_at)
    VALUES (:sourceId, :kind, :sessionId, :siteId, :floorId, :nodeReference, :score, :category, :x, :y, :occurredAt,
      :recordedAt)
    ON CONFLICT DO NOTHING
    """;

  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  public JdbcFeedbackFactRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public boolean recordOnce(FeedbackFact fact) {
    var parameters = new MapSqlParameterSource("sourceId", fact.sourceId())
      .addValue("kind", fact.kind().name())
      .addValue("sessionId", fact.navigationSessionId(), Types.OTHER)
      .addValue("siteId", fact.siteId())
      .addValue("floorId", fact.floorId(), Types.OTHER)
      .addValue("nodeReference", fact.nodeReference(), Types.VARCHAR)
      .addValue("score", fact.score(), Types.SMALLINT)
      .addValue("category", fact.category() == null ? null : fact.category().name(), Types.VARCHAR)
      .addValue("x", fact.x(), Types.DOUBLE)
      .addValue("y", fact.y(), Types.DOUBLE)
      .addValue("occurredAt", Timestamp.from(fact.occurredAt()))
      .addValue("recordedAt", Timestamp.from(clock.instant()));
    return jdbc.update(INSERT, parameters) == 1;
  }
}
