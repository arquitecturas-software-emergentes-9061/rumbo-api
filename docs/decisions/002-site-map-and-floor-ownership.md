# ADR 002 — Site-wide indoor map and floor ownership

Status: accepted for the current Planning model.

## Context

Navigation queries a published spatial graph by `siteId`, so a site must have an unambiguous current map. Planning stores floor IDs on plan versions, but previously had no persistent rule preventing the same floor ID from being used by maps of different sites.

## Decision

- One `IndoorMap` belongs to each site. Map versions, including drafts and archived versions, belong to that same map.
- A floor ID has one owning site in `planning_floor_sites`. Saving a draft registers its floors or rejects a floor already owned by another site. A cross-floor connection can only join nodes registered within the same map aggregate and must use `STAIRS`, `RAMP`, or `ELEVATOR`.
- The Planning export returns only the currently `PUBLISHED` version. Navigation translates it at its own boundary and does not query Planning tables or import its JPA entities.

## Consequences

Flyway V3 adds uniqueness constraints and backfills floor ownership from existing plans. Applying V3 to a database that already contains multiple maps for one site or a floor ID shared by different sites requires resolving those conflicts before migration. This registry enforces consistency of stored floor IDs; it does not yet establish an administrative Site/Floor API or site authorization.

The exported core includes scales, nodes, weighted links, and versioned medical destinations tied to destination nodes. Accessibility restrictions and obstacles still need their own modeled contracts before PostgreSQL-backed routes can be exposed.
