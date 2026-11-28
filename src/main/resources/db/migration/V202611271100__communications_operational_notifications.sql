-- Communications (US-31, US-32, TS-35, TS-36): notifications about a site, idempotent per source event, with
-- priority, an email channel and a scheduled retry. site_id and source_event_id are references by value only.

ALTER TABLE communications_notifications
    ADD COLUMN site_id         uuid,
    ADD COLUMN source_event_id uuid,
    ADD COLUMN priority        varchar(10) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN next_attempt_at timestamptz;

ALTER TABLE communications_notifications DROP CONSTRAINT ck_communications_notifications_type;
ALTER TABLE communications_notifications ADD CONSTRAINT ck_communications_notifications_type
    CHECK (type IN ('WELCOME', 'ACCOUNT_LOCKED', 'PASSWORD_CHANGED', 'SUBSCRIPTION_ACTIVATED', 'INCIDENT_REPORTED',
                    'MAP_PUBLISHED'));
ALTER TABLE communications_notifications DROP CONSTRAINT ck_communications_notifications_channel;
ALTER TABLE communications_notifications ADD CONSTRAINT ck_communications_notifications_channel
    CHECK (channel IN ('IN_APP', 'EMAIL'));
ALTER TABLE communications_notifications ADD CONSTRAINT ck_communications_notifications_priority
    CHECK (priority IN ('NORMAL', 'HIGH'));
-- A final delivery has nothing left to schedule.
ALTER TABLE communications_notifications ADD CONSTRAINT ck_communications_notifications_next_attempt
    CHECK (delivery_status IN ('PENDING', 'RETRY_PENDING') OR next_attempt_at IS NULL);

-- The same event never notifies the same account twice, however many times it arrives.
CREATE UNIQUE INDEX ux_communications_notifications_source
    ON communications_notifications (source_event_id, recipient_user_id) WHERE source_event_id IS NOT NULL;
-- Deliveries waiting for an attempt, read by the retry job.
CREATE INDEX ix_communications_notifications_due
    ON communications_notifications (next_attempt_at) WHERE delivery_status IN ('PENDING', 'RETRY_PENDING');
-- Notification center filtered by site.
CREATE INDEX ix_communications_notifications_recipient_site
    ON communications_notifications (recipient_user_id, site_id, created_at DESC);
