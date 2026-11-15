CREATE TABLE identity_onboarding_requests (
    id               uuid PRIMARY KEY,
    request_type     varchar(20)   NOT NULL CHECK (request_type IN ('DEMO', 'ONBOARDING')),
    requester_name   varchar(120)  NOT NULL,
    email            varchar(254)  NOT NULL,
    clinic_name      varchar(120)  NOT NULL,
    requester_role   varchar(120)  NOT NULL,
    sites_range      varchar(20)   NULL CHECK (sites_range IN ('ONE', 'TWO_TO_THREE', 'FOUR_OR_MORE')),
    message          varchar(1000) NULL,
    consent_given_at timestamptz   NOT NULL,
    submitted_at     timestamptz   NOT NULL
);

CREATE INDEX identity_onboarding_requests_submitted_idx ON identity_onboarding_requests (submitted_at DESC);
