-- Communications: in-app notifications generated from platform events.
-- recipient_user_id references identity_users.id by value only: no foreign key across bounded contexts.
-- delivery_status tracks the channel delivery (PENDING, SENT, RETRY_PENDING, FAILED); read_at is NULL while unread.

CREATE TABLE communications_notifications (
    id                 uuid          PRIMARY KEY,
    recipient_user_id  uuid          NOT NULL,
    type               varchar(40)   NOT NULL,
    channel            varchar(20)   NOT NULL,
    detail             varchar(255),
    delivery_status    varchar(20)   NOT NULL,
    delivery_attempts  integer       NOT NULL DEFAULT 0,
    last_attempt_at    timestamptz,
    read_at            timestamptz,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    CONSTRAINT ck_communications_notifications_type
        CHECK (type IN ('WELCOME', 'ACCOUNT_LOCKED', 'PASSWORD_CHANGED', 'SUBSCRIPTION_ACTIVATED')),
    CONSTRAINT ck_communications_notifications_channel CHECK (channel IN ('IN_APP')),
    CONSTRAINT ck_communications_notifications_delivery_status
        CHECK (delivery_status IN ('PENDING', 'SENT', 'RETRY_PENDING', 'FAILED')),
    CONSTRAINT ck_communications_notifications_attempts CHECK (delivery_attempts >= 0)
);

CREATE INDEX ix_communications_notifications_recipient
    ON communications_notifications (recipient_user_id, created_at DESC, id DESC);
CREATE INDEX ix_communications_notifications_unread
    ON communications_notifications (recipient_user_id) WHERE read_at IS NULL;
