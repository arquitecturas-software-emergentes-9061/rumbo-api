# Rumbo API — Planning and Navigation contract

Status: agreed target contract for future iterations. Sections marked **target** are not implemented yet. See [ADR 001](decisions/001-architecture-and-contract.md) for the decisions behind this contract.

## Published indoor model — spatial core and destinations implemented

`planning` owns the editable spatial model. Its `PlanningPublishedMapFacade` in `interfaces/acl` exports the current `PUBLISHED` graph for one site; the implementation in `application/acl` reads through a Planning repository port. An adapter in `navigation` translates that public contract to `PublishedSpatialGraph`. The implemented core contains `siteId`, `mapId`, `modelVersionId`, floor-plan bounds and scales, nodes, typed connections with physical distances, persisted weights, vertical penalties and direction, and medical directory entries tied to destination nodes. It returns no draft or archived version and has no public HTTP endpoint. PostgreSQL enforces one indoor map per site and one owning site per floor ID ([ADR 002](decisions/002-site-map-and-floor-ownership.md)).

The full exchange contract required before real route calculation must additionally contain:

- Accessibility restrictions and traversability state for connections.
- Obstacles needed to validate traversable connections.

Accessibility restrictions, traversability state, and obstacles are **not yet persisted in the published spatial core**. Directory metadata does not establish that a route is accessible. A* already calculates minimum-cost routes internally from a published graph, but the PostgreSQL navigation route endpoint remains unavailable until the missing rules and authorization are implemented. The existing `demo` route endpoint remains separate.

### Coordinate convention for Planning

Each floor-plan version defines an unscaled rectangular visual coordinate space with positive `widthUnits` and `heightUnits`. Its origin is the top-left corner: x increases rightward and y downward. Coordinates include `floorId` and `planVersionId`; they must be finite and inside the inclusive bounds `(0, 0)` to `(widthUnits, heightUnits)`. A rendered or resized image must transform those coordinates for display rather than replacing the stored plan coordinates.

A confirmed `MapScale` is tied to `floorId` and `planVersionId` and expresses `metersPerUnit = realDistanceMeters / EuclideanDistanceInPlanUnits` between two distinct reference points of that version. Physical connection distance is expressed in meters; routing weight is a separate positive cost. A plan without a confirmed physical reference has no confirmed scale. `planning` stores and reloads these values in PostgreSQL and exports the published spatial core to `navigation` through a read-only contract.

`navigation` reads never return drafts. Publishing a new version is atomic: a rejected draft leaves the previous published version available. Authorization of the consumer and site is required before serving real models; it is not implemented yet.

## Draft graph editing — implemented with explicit local enablement

`GET /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph`

`PUT /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph`

Both operations address an **existing DRAFT** version; they do not create a map, version, floor plan, or scale. The `GET` response contains `siteId`, `mapId`, `modelVersionId`, `status`, `plans[]` with coordinate bounds and optional `metersPerUnit`, `nodes[]`, `connections[]`, and `destinations[]`. `PUT` replaces nodes, connections, and directory destinations atomically, preserving registered plans and confirmed scales. Each directory entry has `id`, `nodeId`, `type` (`DOCTOR`, `SPECIALTY`, or `SERVICE`), `name`, and optional `specialty`. Its node must exist in that version with type `DESTINATION`. Omitting `destinations` is equivalent to an empty list, so a full replacement without that field removes existing directory metadata. A connection may include `verticalPenalty`, a nonnegative routing cost added to `weight` when it crosses floors; omission means `0`, and a nonzero value on a same-floor connection is rejected. `GET` always returns the stored value. An empty collection is allowed while editing; publication still requires a valid navigable graph. The endpoint requires the `postgres` profile and `RUMBO_PLANNING_EDITING_ENABLED=true`, and is disabled by default until site authorization exists.

Example `PUT` body (all shown fields are required; `verticalPenalty`, `destinations`, and each destination's `specialty` are optional):

```json
{
  "nodes": [
    {"id": "00000000-0000-4000-8000-000000000010", "floorId": "00000000-0000-4000-8000-000000000002", "planVersionId": "00000000-0000-4000-8000-000000000020", "x": 12, "y": 8, "type": "ACCESS"},
    {"id": "00000000-0000-4000-8000-000000000011", "floorId": "00000000-0000-4000-8000-000000000002", "planVersionId": "00000000-0000-4000-8000-000000000020", "x": 24, "y": 8, "type": "DESTINATION"}
  ],
  "connections": [
    {"id": "00000000-0000-4000-8000-000000000030", "sourceNodeId": "00000000-0000-4000-8000-000000000010", "targetNodeId": "00000000-0000-4000-8000-000000000011", "type": "CORRIDOR", "distanceMeters": 3, "weight": 4, "bidirectional": true}
  ],
  "destinations": [
    {"id": "00000000-0000-4000-8000-000000000040", "nodeId": "00000000-0000-4000-8000-000000000011", "type": "DOCTOR", "name": "Dra. Núñez", "specialty": "Cardiología"}
  ]
}
```

- `200 OK`: draft graph snapshot after a successful read or replacement.
- `400 Bad Request`: malformed UUID, JSON, required field, or enum value.
- `404 Not Found`: site, map, and version do not identify an existing draft.
- `409 Conflict`: a replacement targets a version no longer in `DRAFT` state.
- `422 Unprocessable Entity`: invalid graph element; `code: "INVALID_MAP_GRAPH"` and `issues[]` locate the offending node, connection, or plan by `elementType` and `elementId`. The stored draft is unchanged.
- Invalid directory entries also return `422` with `elementType: "DESTINATION"`; missing or non-destination nodes are rejected before saving.

Concurrent replacements are serialized by locking the draft version. The API does not yet provide an edit revision or conditional request; clients must coordinate concurrent editor sessions to avoid a later valid replacement overwriting an earlier one.

## Map version publication — implemented with explicit local enablement

`PATCH /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}`

Request media type: `application/merge-patch+json`; body: `{"status":"PUBLISHED"}`. All three path IDs are required UUIDs. The endpoint is active only with the `postgres` profile and `RUMBO_PLANNING_PUBLICATION_ENABLED=true`; keep it disabled outside controlled local testing until site authorization is implemented.

- `200 OK`: `siteId`, `mapId`, `modelVersionId`, `status: "PUBLISHED"`. The previous published version, if any, becomes `ARCHIVED` in the same transaction.
- `400 Bad Request`: malformed ID or JSON, or a status other than `PUBLISHED`.
- `404 Not Found`: the site, map and version do not match an existing draft.
- `409 Conflict`: the version exists but is no longer `DRAFT`.
- `422 Unprocessable Entity`: graph validation failed; response `code: "INVALID_MAP_GRAPH"` and `issues[]` with `code`, `elementType`, `elementId` (null only for graph-wide issues) and `message`. The draft remains `DRAFT` and the previous `PUBLISHED` version is unchanged.

Publication currently requires a confirmed scale for every plan, at least one access and destination, one connected graph, and directed reachability of each destination. Cross-floor links must be stairs, ramp, or elevator when added to the draft. Obstacle and accessibility rules belong to later work.

## Destination search — implemented with `demo` data

`GET /api/v1/navigations/destinations?siteId={uuid}&query={text}&accessibleOnly={boolean}`

`siteId` is required; `query` is optional and `accessibleOnly` defaults to `false`. The current endpoint searches name, specialty and category without case or accent sensitivity and returns destinations for the requested site. Its current source is the `demo` repository, not a persisted published model.

## Published medical directory — implemented with explicit local enablement

`GET /api/v1/sites/{siteId}/directory-entries?q={term}`

`siteId` is a required UUID path parameter, and a non-blank `q` of at most 150 characters is a required query parameter. Search is case and accent insensitive and matches prefixes of every query token against name, specialty, and entry type. Results are ordered by name and ID and limited to 50. PostgreSQL uses a GIN text-search index. Only entries in the site's current `PUBLISHED` map version with a node of type `DESTINATION` are returned; drafts, archived versions, foreign sites, and invalid destination-node references are excluded.

- `200 OK`: an array of `{id, siteId, modelVersionId, floorId, nodeId, type, name, specialty}`; an unmatched term returns `[]`.
- `400 Bad Request`: missing or malformed `siteId`, or missing, blank, or overlong `q`; body `{code: "INVALID_REQUEST", message: "..."}`.

This endpoint requires the `postgres` profile and `RUMBO_NAVIGATION_DIRECTORY_ENABLED=true`. It is disabled by default until site authorization is implemented. This directory does not claim route accessibility or replace the separate `demo` destination endpoint. The Sprint Backlog's global `q`-only URL was scoped under the site resource because the current backend has no authenticated site context. The path identifies the site's directory collection; `q` filters its entries.

## Route calculation — TS-13 target

The internal TS-12 calculator reads only the current published graph through the `navigation` repository port. A* minimizes the sum of each traversed connection's persisted `weight + verticalPenalty`, follows one-way links, and returns no route when the goal is unreachable. On a single floor-plan coordinate system it scales Euclidean distance by the minimum observed cost per geometric meter across links, giving a lower bound on remaining cost. For mixed floors or plans, it uses a zero heuristic. This internal calculation has no public PostgreSQL route endpoint yet and does not enforce accessibility or obstacle rules, which are not in the published model.

`GET /api/v1/sites/{siteId}/routes?from={nodeId}&to={nodeId}&accessibleOnly={boolean}`

- `siteId`, `from` and `to` are required UUIDs. `accessibleOnly` is optional and defaults to `false`.
- Both nodes must belong to the site and a coherent published version. Calculation uses persisted graph weights, requested accessibility restrictions and vertical connection penalties. A route cannot cross modeled obstacles or include nonexistent links.
- `200 OK`: `siteId`, `modelVersionId`, `totalDistanceMeters`, `totalCost`, ordered `nodes[]` and ordered `links[]`. Each node identifies its floor, node and coordinates; each link identifies its endpoints, type, physical distance, persisted weight, vertical penalty and accessibility. `totalCost` sums applied weights and penalties and can differ from physical distance.
- `400 Bad Request`: invalid parameters, or existing nodes from different sites or outside `{siteId}`.
- `404 Not Found`: a node or published model is missing, or no compatible route exists. Never return a partial route.
- `422 Unprocessable Entity`: a published model exists but its data is invalid for route calculation. Publication should prevent this state; this response is defensive.

TS-13 targets a response under 200 ms in its acceptance scenario. Measurement conditions will be defined and tested during implementation. Authentication and site authorization responses will be specified with IAM before real data is exposed.

### Current provisional endpoint

Today, `POST /api/v1/navigations/route` takes `siteId`, origin and destination floor/node IDs, and `accessibleOnly` in a JSON body. It uses Dijkstra over `demo` data and returns `totalDistanceMeters`, `nodes[]` and `links[]`; validation errors map to `422`. This endpoint does not implement the target contract above and will be reviewed when TS-12 and TS-13 are delivered. No compatibility requirement is assumed for clients that have not yet been integrated.

## Shared rules

- `navigation` only reads `PUBLISHED` models and never changes `planning` drafts or resources.
- An accessibility preference must not be bypassed to fabricate a route.
- Cross-floor links must be explicit vertical connectors in the published graph.
- Clients pass node identifiers, not destination display names, when requesting routes.
- `demo` fixtures are not a production data source.
