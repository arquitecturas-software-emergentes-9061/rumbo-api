# Rumbo API — Planning and Navigation contract

Status: agreed target contract for future iterations. Sections marked **target** are not implemented yet. See [ADR 001](decisions/001-architecture-and-contract.md) for the decisions behind this contract.

## Published indoor model — spatial core implemented, full model target

`planning` owns the editable spatial model. Its `PublishedMapReader` now exports the current `PUBLISHED` graph for one site; an adapter in `navigation` translates it to `PublishedSpatialGraph`. That implemented core contains `siteId`, `mapId`, `modelVersionId`, floor-plan bounds and scales, nodes, and typed connections with physical distances, persisted weights, and direction. It returns no draft or archived version and has no public HTTP endpoint. PostgreSQL now enforces one indoor map per site and one owning site per floor ID ([ADR 002](decisions/002-site-map-and-floor-ownership.md)).

The full exchange contract required before real route calculation must additionally contain:

- Accessibility restrictions and traversability state for connections.
- Destinations linked to valid published nodes and obstacles needed to validate traversable connections.

Accessibility restrictions, traversability state, destination metadata, and obstacles are **not yet persisted in the published spatial core**. The PostgreSQL navigation route endpoint remains unavailable until those rules and A* are implemented. The existing `demo` route endpoint remains separate.

### Coordinate convention for Planning

Each floor-plan version defines an unscaled rectangular visual coordinate space with positive `widthUnits` and `heightUnits`. Its origin is the top-left corner: x increases rightward and y downward. Coordinates include `floorId` and `planVersionId`; they must be finite and inside the inclusive bounds `(0, 0)` to `(widthUnits, heightUnits)`. A rendered or resized image must transform those coordinates for display rather than replacing the stored plan coordinates.

A confirmed `MapScale` is tied to `floorId` and `planVersionId` and expresses `metersPerUnit = realDistanceMeters / EuclideanDistanceInPlanUnits` between two distinct reference points of that version. Physical connection distance is expressed in meters; routing weight is a separate positive cost. A plan without a confirmed physical reference has no confirmed scale. `planning` stores and reloads these values in PostgreSQL and exports the published spatial core to `navigation` through a read-only contract.

`navigation` reads never return drafts. Publishing a new version is atomic: a rejected draft leaves the previous published version available. Authorization of the consumer and site is required before serving real models; it is not implemented yet.

## Draft graph editing — implemented with explicit local enablement

`GET /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph`

`PUT /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph`

Both operations address an **existing DRAFT** version; they do not create a map, version, floor plan, or scale. The `GET` response contains `siteId`, `mapId`, `modelVersionId`, `status`, `plans[]` with coordinate bounds and optional `metersPerUnit`, `nodes[]`, and `connections[]`. `PUT` replaces the complete node and connection collections atomically, preserving registered plans and confirmed scales. An empty collection is allowed while editing; publication still requires a valid navigable graph. The endpoint requires the `postgres` profile and `RUMBO_PLANNING_EDITING_ENABLED=true`, and is disabled by default until site authorization exists.

Example `PUT` body (all fields are required for each element):

```json
{
  "nodes": [
    {"id": "00000000-0000-4000-8000-000000000010", "floorId": "00000000-0000-4000-8000-000000000002", "planVersionId": "00000000-0000-4000-8000-000000000020", "x": 12, "y": 8, "type": "ACCESS"},
    {"id": "00000000-0000-4000-8000-000000000011", "floorId": "00000000-0000-4000-8000-000000000002", "planVersionId": "00000000-0000-4000-8000-000000000020", "x": 24, "y": 8, "type": "DESTINATION"}
  ],
  "connections": [
    {"id": "00000000-0000-4000-8000-000000000030", "sourceNodeId": "00000000-0000-4000-8000-000000000010", "targetNodeId": "00000000-0000-4000-8000-000000000011", "type": "CORRIDOR", "distanceMeters": 3, "weight": 4, "bidirectional": true}
  ]
}
```

- `200 OK`: draft graph snapshot after a successful read or replacement.
- `400 Bad Request`: malformed UUID, JSON, required field, or enum value.
- `404 Not Found`: site, map, and version do not identify an existing draft.
- `409 Conflict`: a replacement targets a version no longer in `DRAFT` state.
- `422 Unprocessable Entity`: invalid graph element; `code: "INVALID_MAP_GRAPH"` and `issues[]` locate the offending node, connection, or plan by `elementType` and `elementId`. The stored draft is unchanged.

Concurrent replacements are serialized by locking the draft version. The API does not yet provide an edit revision or conditional request; clients must coordinate concurrent editor sessions to avoid a later valid replacement overwriting an earlier one.

## Map version publication — implemented with explicit local enablement

`PATCH /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}`

Request media type: `application/merge-patch+json`; body: `{"status":"PUBLISHED"}`. All three path IDs are required UUIDs. The endpoint is active only with the `postgres` profile and `RUMBO_PLANNING_PUBLICATION_ENABLED=true`; keep it disabled outside controlled local testing until site authorization is implemented.

- `200 OK`: `siteId`, `mapId`, `modelVersionId`, `status: "PUBLISHED"`. The previous published version, if any, becomes `ARCHIVED` in the same transaction.
- `400 Bad Request`: malformed ID or JSON, or a status other than `PUBLISHED`.
- `404 Not Found`: the site, map and version do not match an existing draft.
- `409 Conflict`: the version exists but is no longer `DRAFT`.
- `422 Unprocessable Entity`: graph validation failed; response `code: "INVALID_MAP_GRAPH"` and `issues[]` with `code`, `elementType`, `elementId` (null only for graph-wide issues) and `message`. The draft remains `DRAFT` and the previous `PUBLISHED` version is unchanged.

Publication currently requires a confirmed scale for every plan, at least one access and destination, one connected graph, and directed reachability of each destination. Cross-floor links must be stairs, ramp, or elevator when added to the draft. Obstacle, accessibility and destination metadata rules belong to later work.

## Destination search — implemented with `demo` data

`GET /api/v1/navigations/destinations?siteId={uuid}&query={text}&accessibleOnly={boolean}`

`siteId` is required; `query` is optional and `accessibleOnly` defaults to `false`. The current endpoint searches name, specialty and category without case or accent sensitivity and returns destinations for the requested site. Its current source is the `demo` repository, not a persisted published model.

## Route calculation — TS-13 target

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
