-- Analytics (TS-30): anonymized facts of each navigation session, written once and only read by aggregation.
-- There is no user column on purpose; site, floor and nodes are kept by identifier only, without foreign keys,
-- so Analytics stays independent of the contexts that own them.

CREATE TABLE navigation_events (
    id                    uuid         PRIMARY KEY,
    navigation_session_id uuid         NOT NULL,
    site_id               uuid         NOT NULL,
    floor_id              uuid,
    event_type            varchar(30)  NOT NULL,
    node_reference        varchar(100),
    occurred_at           timestamptz  NOT NULL,
    received_at           timestamptz  NOT NULL,
    metadata              jsonb        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_navigation_events_type CHECK (event_type IN
        ('NAVIGATION_STARTED', 'DEVIATION_DETECTED', 'ROUTE_RECALCULATED', 'NAVIGATION_FINISHED'))
);

-- Every metric is computed per site and period.
CREATE INDEX ix_navigation_events_site_occurred ON navigation_events (site_id, occurred_at);
-- Session-level checks, such as "was this session finished" before accepting a rating (TS-31).
CREATE INDEX ix_navigation_events_session_type ON navigation_events (navigation_session_id, event_type);
