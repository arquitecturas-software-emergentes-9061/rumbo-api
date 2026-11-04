# ADR 005 — Successor draft from the current published map

Status: accepted and implemented for local review.

## Context

Publishing freezes the validated graph that Navigation reads. The editor could previously
edit only an existing draft, so a published map had no supported path for later corrections.
The initial Sprint Backlog did not specify how to create a successor map version.

## Decision

- `POST /api/v1/sites/{siteId}/maps/{mapId}/versions` accepts the expected current
  `sourceVersionId` and creates one new `DRAFT` version. The response is the copied
  graph with a new `modelVersionId`, `Location` pointing to its graph, and an initial
  `ETag` for subsequent `PUT` requests.
- The new version keeps the same map and site. It copies floor-plan bounds and scales,
  node and connection IDs, routing metrics, accessibility declarations, and medical
  destinations. Versioned records belong to the new model version; the published
  source remains untouched.
- At most one draft exists per map. Creation and publication serialize on the map row;
  a duplicate draft or an outdated source is rejected with `409`.
- The editor uses the existing `GET`/`PUT` graph contract and `If-Match` to change the
  successor. Publishing it archives the previous `PUBLISHED` version atomically.
- This workflow does not perform US-09/US-10 regeneration or destination matching.
  That remains a separate proposal and reconciliation process.

## Consequences

The lifecycle now supports further edits without changing the graph used by Navigation
until the successor is published. Initial creation of a map, floor plan, and physical
scale still needs its own administrative workflow and site authorization before
external use. See [API-CONTRACT.md](../API-CONTRACT.md) for request and error details.
