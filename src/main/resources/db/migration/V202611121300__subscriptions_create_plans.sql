CREATE TABLE subscriptions_plans (
    id uuid PRIMARY KEY,
    code varchar(30) UNIQUE NOT NULL CHECK (code IN ('BASIC', 'PROFESSIONAL', 'ENTERPRISE')),
    name varchar(60) NOT NULL,
    price_amount numeric(10,2) NOT NULL CHECK (price_amount > 0),
    currency char(3) NOT NULL,
    billing_interval varchar(10) NOT NULL CHECK (billing_interval = 'MONTH'),
    purchase_mode varchar(20) NOT NULL CHECK (purchase_mode IN ('CHECKOUT', 'CONTACT_SALES')),
    display_order int NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz,
    updated_at timestamptz
);

INSERT INTO subscriptions_plans
    (id, code, name, price_amount, currency, billing_interval, purchase_mode, display_order, active, created_at, updated_at)
VALUES
    ('79000000-0000-0000-0000-000000000001', 'BASIC', 'Básico', 39.90, 'PEN', 'MONTH', 'CHECKOUT', 1, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('79000000-0000-0000-0000-000000000002', 'PROFESSIONAL', 'Profesional', 49.99, 'PEN', 'MONTH', 'CHECKOUT', 2, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('79000000-0000-0000-0000-000000000003', 'ENTERPRISE', 'Enterprise', 59.99, 'PEN', 'MONTH', 'CONTACT_SALES', 3, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
