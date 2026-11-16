-- Historical orders retain NULL billing fields. Every new order requires a validated snapshot.
ALTER TABLE subscriptions_payment_orders
  ADD COLUMN billing_legal_name varchar(160) NULL,
  ADD COLUMN billing_ruc char(11) NULL,
  ADD COLUMN billing_fiscal_address varchar(200) NULL,
  ADD COLUMN billing_email varchar(254) NULL,
  ADD COLUMN billing_purchase_order varchar(40) NULL,
  ADD COLUMN paid_at timestamptz NULL;
