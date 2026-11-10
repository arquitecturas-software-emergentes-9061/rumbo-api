# ADR 003 — Complete draft edits and regeneration

Status: accepted; complete edits and revision preconditions implemented, regeneration deferred to the US-09 proposal workflow.

## Context

The editor saves an existing draft graph in one operation. A previous `PUT` treated omitted `destinations` as an empty list, which could remove registered medical entries by accident. A row lock serialized writes but did not prevent a later editor from overwriting an earlier valid save. US-10 requires destinations to survive regeneration of their corresponding rooms, but the current model has neither generated room identity nor a US-09 proposal to match against.

## Decision

- `PUT /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph` replaces the complete **editable** graph: `nodes`, `connections`, and `destinations` must all be present. An explicit empty array clears one collection. Plans, scales and map identity remain server-managed.
- `GET` returns the current draft `ETag`. `PUT` requires that value in `If-Match`. The stored revision is checked under the draft version lock and advances atomically with a valid replacement. Missing, malformed and stale preconditions return `428`, `400` and `412`, respectively.
- Regeneration will have a separate proposal and reconciliation step with US-09. It must preserve destination names and categories only when the corresponding room can be identified; uncertain matches require editor review. The proposal must not mutate the stored draft. The editor will save the approved complete graph with `PUT` and the latest `If-Match` value.

## Consequences

The draft editing request contract changes: clients must send all three collections and the current `If-Match`. No generic partial `PATCH` is introduced for graph collections. WI-US10-03 remains open until US-09 provides a candidate room model and a reliable matching contract; silently keeping destinations on a generic replacement would not establish their correct room references. See [API-CONTRACT.md](../API-CONTRACT.md) for HTTP details.

## Geometry extension (2026-11-10)

The same revision-checked `PUT` accepts versioned `walls[]` and `doors[]` alongside the three required graph collections. A wall belongs to a registered floor-plan coordinate space; a door identifies its wall and an opening along it. Omitted geometry collections preserve stored geometry for older clients, while explicit arrays replace them. Planning validates that route connections do not cross solid walls and that a crossing through an opening is `DOORWAY`. The original plan file stays in floor-plan storage. Successor drafts copy the published version's geometry.
