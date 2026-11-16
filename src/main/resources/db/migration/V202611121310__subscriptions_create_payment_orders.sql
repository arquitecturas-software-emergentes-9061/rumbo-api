CREATE TABLE subscriptions_payment_orders (
  id uuid PRIMARY KEY,
  payer_user_id uuid NOT NULL,
  plan_code varchar(30) NOT NULL REFERENCES subscriptions_plans(code),
  amount numeric(10,2) NOT NULL,
  currency char(3) NOT NULL,
  status varchar(20) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'EXPIRED', 'CANCELLED')),
  provider_session_id varchar(255) UNIQUE,
  created_at timestamptz,
  updated_at timestamptz
);
CREATE INDEX idx_subscriptions_payment_orders_payer ON subscriptions_payment_orders(payer_user_id);
