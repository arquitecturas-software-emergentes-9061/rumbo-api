-- Analytics (TS-31, US-25): anonymized facts of the ratings and incident reports of the visitors.
-- The rating or report itself is stored by its registration; this table only keeps what the dashboard aggregates,
-- without visitor or comment. source_id is the id of that rating or report, so a repeated event is ignored.

CREATE TABLE analytics_feedback_facts (
    source_id             uuid              PRIMARY KEY,
    kind                  varchar(10)       NOT NULL,
    navigation_session_id uuid,
    site_id               uuid              NOT NULL,
    floor_id              uuid,
    node_reference        varchar(100),
    score                 smallint,
    category              varchar(30),
    position_x            double precision,
    position_y            double precision,
    occurred_at           timestamptz       NOT NULL,
    recorded_at           timestamptz       NOT NULL,
    CONSTRAINT ck_analytics_feedback_facts_kind CHECK (kind IN ('RATING', 'INCIDENT')),
    CONSTRAINT ck_analytics_feedback_facts_rating CHECK (kind <> 'RATING'
        OR (score BETWEEN 1 AND 5 AND category IS NULL AND navigation_session_id IS NOT NULL)),
    CONSTRAINT ck_analytics_feedback_facts_incident CHECK (kind <> 'INCIDENT'
        OR (score IS NULL AND category IN ('BLOCKED_PATH', 'WRONG_DIRECTIONS', 'MAP_MISMATCH', 'SIGNAL_LOST',
                                           'ACCESSIBILITY_BARRIER', 'OTHER'))),
    CONSTRAINT ck_analytics_feedback_facts_position CHECK ((position_x IS NULL) = (position_y IS NULL)
        AND (position_x IS NULL OR floor_id IS NOT NULL))
);

-- A session is rated once: a second rating of the same session is a repetition.
CREATE UNIQUE INDEX ux_analytics_feedback_facts_session_rating
    ON analytics_feedback_facts (navigation_session_id) WHERE kind = 'RATING';
CREATE INDEX ix_analytics_feedback_facts_site_occurred ON analytics_feedback_facts (site_id, occurred_at);
CREATE INDEX ix_analytics_feedback_facts_session ON analytics_feedback_facts (navigation_session_id);
