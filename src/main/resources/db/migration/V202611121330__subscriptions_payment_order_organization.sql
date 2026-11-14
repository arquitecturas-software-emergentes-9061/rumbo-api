ALTER TABLE subscriptions_payment_orders
  ADD COLUMN organization_id uuid NOT NULL,
  ADD COLUMN organization_name varchar(160) NOT NULL;

CREATE INDEX idx_subscriptions_payment_orders_organization_id
  ON subscriptions_payment_orders (organization_id);
