# ADR 004 — Local default exposes the implemented API

Status: accepted for local development; revises the default enablement described in
[ADR 001](001-architecture-and-contract.md).

## Context

The team needs to review and exercise all implemented endpoints in Swagger without
selecting profiles or setting four feature flags in each IDE run configuration.
The backend still lacks site authorization and obstacle validation, so the local
review configuration must not be exposed to other machines.

## Decision

The single `application.properties` uses `postgres,local` as its default profiles,
enables all four controlled HTTP feature flags, and binds the server to `127.0.0.1`.
The two provisional demo operations remain clearly labeled and use only their fixed
fixture; the original five PostgreSQL operations now include a sixth for creating
successor drafts ([ADR 005](005-successor-draft-versions.md)). A local initializer creates
separate draft and published examples after Flyway migration. Credentials remain
environment variables, and Docker Compose is optional when PostgreSQL is already
available.

## Consequences

Normal local startup requires PostgreSQL and its connection variables. The default
is suitable for demonstration on the developer's own machine, not deployment or
access by other machines. Feature flags can still disable individual HTTP APIs.
Site authorization and obstacle validation remain prerequisites for external use.
