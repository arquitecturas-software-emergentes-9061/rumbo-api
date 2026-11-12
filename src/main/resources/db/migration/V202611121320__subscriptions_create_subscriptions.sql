CREATE TABLE subscriptions_subscriptions (
    id uuid PRIMARY KEY,
    owner_user_id uuid UNIQUE NOT NULL,
    plan_code varchar(30) NOT NULL REFERENCES subscriptions_plans(code),
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED')),
    current_period_start timestamptz NOT NULL,
    current_period_end timestamptz NOT NULL,
    provider_subscription_id varchar(255),
    created_at timestamptz,
    updated_at timestamptz,
    CHECK (current_period_end > current_period_start)
);
CREATE TABLE subscriptions_processed_payment_events (
    event_id varchar(255) PRIMARY KEY,
    event_type varchar(100) NOT NULL,
    processed_at timestamptz NOT NULL
);
