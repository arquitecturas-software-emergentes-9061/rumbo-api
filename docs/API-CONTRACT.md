# Rumbo API — Planning and Navigation contract

Status: implemented contracts are marked with their enablement conditions; remaining requirements are identified below. See [ADR 001](decisions/001-architecture-and-contract.md) for the domain and route decisions, [ADR 004](decisions/004-local-default-api-visibility.md) for the local startup policy, and [ADR 005](decisions/005-successor-draft-versions.md) for successor map versions.

Normal local startup defaults to the `postgres,local` profiles, enables the four HTTP
feature flags, and registers operations from Planning, Navigation, IAM,
and Analytics in Swagger. The two provisional
operations are tagged **Demo navigation**. The server binds to loopback and seeds
separate draft and published example sites after Flyway migration. PostgreSQL and
private connection variables, a JWT signing key, and Redis are required. The real
Planning and Navigation operations require a `CLINIC_ADMIN` JWT and ownership of the
requested site; the fixed local example maps currently have no IAM owner. See the
README for startup commands and IDs.

## Published indoor model — spatial core and destinations implemented

`planning` owns the editable spatial model. Its `PlanningPublishedMapFacade` in `interfaces/acl` exports the current `PUBLISHED` graph for one site; the implementation in `application/acl` reads through a Planning repository port. An adapter in `navigation` translates that public contract to `PublishedSpatialGraph`. The implemented core contains `siteId`, `mapId`, `modelVersionId`, floor-plan bounds and scales, nodes, typed connections with physical distances, persisted weights, vertical penalties and direction, and medical directory entries tied to destination nodes. It returns no draft or archived version and has no public HTTP endpoint. PostgreSQL enforces one indoor map per site and one owning site per floor ID ([ADR 002](decisions/002-site-map-and-floor-ownership.md)).

The spatial core now also carries each connection's accessibility status: `UNKNOWN`, `ACCESSIBLE`, or `INACCESSIBLE`. Existing rows migrate to `UNKNOWN`; omission when editing also means `UNKNOWN`. The full exchange contract still needs:

- Obstacles needed to validate traversable connections.

Obstacles are **not yet persisted in the published spatial core**. A connection marked `ACCESSIBLE` records an explicit editor assertion, not independently validated obstacle clearance. Directory metadata does not establish that a route is accessible. The PostgreSQL navigation route endpoint has IAM owner authorization; obstacle validation and clinic membership remain pending. The existing `demo` route endpoint remains separate.

### Coordinate convention for Planning

Each floor-plan version defines an unscaled rectangular visual coordinate space with positive `widthUnits` and `heightUnits`. Its origin is the top-left corner: x increases rightward and y downward. Coordinates include `floorId` and `planVersionId`; they must be finite and inside the inclusive bounds `(0, 0)` to `(widthUnits, heightUnits)`. A rendered or resized image must transform those coordinates for display rather than replacing the stored plan coordinates.

A confirmed `MapScale` is tied to `floorId` and `planVersionId` and expresses `metersPerUnit = realDistanceMeters / EuclideanDistanceInPlanUnits` between two distinct reference points of that version. Physical connection distance is expressed in meters; routing weight is a separate positive cost. A plan without a confirmed physical reference has no confirmed scale. `planning` stores and reloads these values in PostgreSQL and exports the published spatial core to `navigation` through a read-only contract.

`navigation` reads never return drafts. Publishing a new version is atomic: a rejected draft leaves the previous published version available. IAM currently authorizes a site through its owning account; membership-based authorization is not implemented yet.

## Successor map version — implemented for local review

`POST /api/v1/sites/{siteId}/maps/{mapId}/versions`

Request body: `{"sourceVersionId":"<current-published-version-uuid>"}`. The site and map must
identify an existing map, and the source must still be its current `PUBLISHED` version.
The operation creates one independent `DRAFT` with a server-generated `modelVersionId`.
It copies plan bounds and scales, nodes, weighted connections including accessibility and
vertical penalties, and medical destinations. Element IDs remain stable across versions;
each stored element is associated with the new model version. The source remains
`PUBLISHED` and continues serving directory and route reads until the successor is
published. A map can have only one draft at a time.

- `201 Created`: complete draft graph, `Location` pointing to
  `/api/v1/sites/{siteId}/maps/{mapId}/versions/{newVersionId}/graph`, and `ETag: "rev-0"`.
- `400 Bad Request`: missing or malformed `sourceVersionId`, site ID, or map ID.
- `404 Not Found`: the site/map has no current published source.
- `409 Conflict`: a draft already exists (`DRAFT_ALREADY_EXISTS`) or the supplied
  source is no longer current (`SOURCE_VERSION_NOT_CURRENT`).
- `422 Unprocessable Entity`: stored published data cannot form a valid draft.

The existing draft `GET`/`PUT` and publication `PATCH` complete the successor workflow.
When the successor publishes, the previous published version becomes `ARCHIVED` in the
same transaction. This endpoint requires an authenticated site-owning administrator; it is
enabled with `postgres` and `rumbo.planning.publication.enabled=true`. It does not create
an initial map or floor plan, and it does not reconcile generated rooms with destinations.

## Draft graph editing — implemented for local review

`GET /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph`

`PUT /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph`

Both operations address an **existing DRAFT** version; they do not create a map, version, floor plan, or scale. The `GET` response contains `siteId`, `mapId`, `modelVersionId`, `status`, `plans[]` with coordinate bounds and optional `metersPerUnit`, `nodes[]`, `connections[]`, and `destinations[]`. `PUT` replaces nodes, connections, and directory destinations atomically, preserving registered plans and confirmed scales. The request must explicitly contain all three collections: `nodes`, `connections`, and `destinations`. An empty array intentionally clears that collection; an omitted collection is a `400` and cannot silently delete stored data. Each directory entry has `id`, `nodeId`, `type` (`DOCTOR`, `SPECIALTY`, or `SERVICE`), `name`, and optional `specialty`. Its node must exist in that version with type `DESTINATION`. A connection may include `verticalPenalty`, a nonnegative routing cost added to `weight` when it crosses floors; omission means `0`, and a nonzero value on a same-floor connection is rejected. `accessibility` is optional (`UNKNOWN`, `ACCESSIBLE`, or `INACCESSIBLE`), defaults to `UNKNOWN`, and is returned by `GET`. An empty collection is allowed while editing; publication still requires a valid navigable graph. The endpoint is enabled by default for loopback-only local review; the feature flag `rumbo.planning.editing.enabled` can disable it.

Example `PUT` body (`verticalPenalty`, `accessibility`, and each destination's `specialty` are optional):

```json
{
  "nodes": [
    {"id": "00000000-0000-4000-8000-000000000010", "floorId": "00000000-0000-4000-8000-000000000002", "planVersionId": "00000000-0000-4000-8000-000000000020", "x": 12, "y": 8, "type": "ACCESS"},
    {"id": "00000000-0000-4000-8000-000000000011", "floorId": "00000000-0000-4000-8000-000000000002", "planVersionId": "00000000-0000-4000-8000-000000000020", "x": 24, "y": 8, "type": "DESTINATION"}
  ],
  "connections": [
    {"id": "00000000-0000-4000-8000-000000000030", "sourceNodeId": "00000000-0000-4000-8000-000000000010", "targetNodeId": "00000000-0000-4000-8000-000000000011", "type": "CORRIDOR", "distanceMeters": 3, "weight": 4, "bidirectional": true, "accessibility": "ACCESSIBLE"}
  ],
  "destinations": [
    {"id": "00000000-0000-4000-8000-000000000040", "nodeId": "00000000-0000-4000-8000-000000000011", "type": "DOCTOR", "name": "Dra. Núñez", "specialty": "Cardiología"}
  ]
}
```

- `200 OK`: draft graph snapshot after a successful read or replacement.
- `400 Bad Request`: malformed UUID, JSON, required collection or field, enum value, or `If-Match` syntax.
- `404 Not Found`: site, map, and version do not identify an existing draft.
- `409 Conflict`: a replacement targets a version no longer in `DRAFT` state.
- `412 Precondition Failed`: the draft changed since the editor read it; response `code: "STALE_DRAFT_GRAPH"`. The editor must GET the latest graph and reconcile its changes before retrying.
- `422 Unprocessable Entity`: invalid graph element; `code: "INVALID_MAP_GRAPH"` and `issues[]` locate the offending node, connection, or plan by `elementType` and `elementId`. The stored draft is unchanged.
- `428 Precondition Required`: a `PUT` omitted `If-Match`; response `code: "PRECONDITION_REQUIRED"`.
- Invalid directory entries also return `422` with `elementType: "DESTINATION"`; missing or non-destination nodes are rejected before saving.

`GET` returns a strong `ETag` such as `"rev-0"`. `PUT` must send that exact value in `If-Match`; on success it returns the next `ETag`, for example `"rev-1"`. Replacements lock and check the persisted draft revision, then increment it in the same transaction. A stale editor cannot overwrite a newer save. The writable collections form one atomic replacement; `plans[]`, scales and version identity are server-managed metadata in the read representation.

Regenerating a graph is a separate future US-09/US-10 proposal workflow. This `PUT` does not infer which new room corresponds to an existing destination; the client must include the approved destination entries. See [ADR 003](decisions/003-draft-graph-editing-and-regeneration.md).

## Map version publication — implemented for local review

`PATCH /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}`

Request media type: `application/merge-patch+json`; body: `{"status":"PUBLISHED"}`. All three path IDs are required UUIDs. The endpoint is enabled by default for loopback-only local review; the feature flag `rumbo.planning.publication.enabled` can disable it. A `CLINIC_ADMIN` JWT and site ownership are required.

- `200 OK`: `siteId`, `mapId`, `modelVersionId`, `status: "PUBLISHED"`. The previous published version, if any, becomes `ARCHIVED` in the same transaction.
- `400 Bad Request`: malformed ID or JSON, or a status other than `PUBLISHED`.
- `404 Not Found`: the site, map and version do not match an existing draft.
- `409 Conflict`: the version exists but is no longer `DRAFT`.
- `422 Unprocessable Entity`: graph validation failed; response `code: "INVALID_MAP_GRAPH"` and `issues[]` with `code`, `elementType`, `elementId` (null only for graph-wide issues) and `message`. The draft remains `DRAFT` and the previous `PUBLISHED` version is unchanged.

Publication currently requires a confirmed scale for every plan, at least one access and destination, one connected graph, and directed reachability of each destination. Cross-floor links must be stairs, ramp, or elevator when added to the draft. It persists each connection's declared accessibility, but obstacle clearance is not yet validated.

## Destination search — implemented with `demo` data

`GET /api/v1/navigations/destinations?siteId={uuid}&query={text}&accessibleOnly={boolean}`

`siteId` is required; `query` is optional and `accessibleOnly` defaults to `false`. The current endpoint searches name, specialty and category without case or accent sensitivity and returns destinations for the requested site. Its current source is the `demo` repository, not a persisted published model.

## Published medical directory — implemented for local review

`GET /api/v1/sites/{siteId}/directory-entries?searchTerm={term}`

`siteId` is a required UUID path parameter, and a non-blank `searchTerm` of at most 150 characters is a required query parameter. Search is case and accent insensitive and matches prefixes of every query token against name, specialty, and entry type. Results are ordered by name and ID and limited to 50. PostgreSQL uses a GIN text-search index. Only entries in the site's current `PUBLISHED` map version with a node of type `DESTINATION` are returned; drafts, archived versions, foreign sites, and invalid destination-node references are excluded.

- `200 OK`: an array of `{id, siteId, modelVersionId, floorId, nodeId, type, name, specialty}`; an unmatched term returns `[]`.
- `400 Bad Request`: missing or malformed `siteId`, or missing, blank, or overlong `searchTerm`; body `{code: "INVALID_REQUEST", message: "..."}`.

This endpoint is enabled by default for loopback-only local review; the feature flag `rumbo.navigation.directory.enabled` can disable it. A `CLINIC_ADMIN` JWT and site ownership are required. This directory does not claim route accessibility or replace the separate `demo` destination endpoint. The Sprint Backlog's global `q`-only URL was scoped under the site resource to make the site explicit. The path identifies the site's directory collection; `searchTerm` filters its entries.

## Published route calculation — implemented for local review

The TS-12 calculator reads only the current published graph through the `navigation` repository port. A* minimizes the sum of each traversed connection's persisted `weight + verticalPenalty`, follows one-way links, and returns no route when the goal is unreachable. On a single floor-plan coordinate system it scales Euclidean distance by the minimum observed cost per geometric meter across links, giving a lower bound on remaining cost. For mixed floors or plans, it uses a zero heuristic. With `accessibleOnly=true`, it traverses only links explicitly marked `ACCESSIBLE`; `UNKNOWN` and `INACCESSIBLE` are excluded. Obstacles are not modeled yet, so an `ACCESSIBLE` status alone cannot prove physical clearance.

`GET /api/v1/sites/{siteId}/routes?from={nodeId}&to={nodeId}&accessibleOnly={boolean}`

- `siteId`, `from` and `to` are required UUIDs. `accessibleOnly` is optional and defaults to `false`.
- Both nodes must belong to the current published version of the site. Calculation uses persisted graph weights, connection accessibility and vertical connection penalties. It cannot include nonexistent links; obstacle crossing is not yet checked.
- `200 OK`: `siteId`, `modelVersionId`, `totalDistanceMeters`, `totalCost`, ordered `nodes[]`, ordered `links[]`, and `verticalSections[]`. Each node identifies its floor, node and coordinates; each link identifies its endpoints, type, physical distance, persisted weight, vertical penalty and accessibility. Each vertical section identifies a cross-floor link, its position in `links[]` (`linkIndex`), both floors and the applied cost. A same-floor route has `verticalSections: []`. `totalCost` sums applied weights and penalties and can differ from physical distance.
- `400 Bad Request`: invalid parameters, or existing nodes from different sites or outside `{siteId}`.
- `404 Not Found`: a node or published model is missing, or no compatible route exists. Never return a partial route.
- `422 Unprocessable Entity`: a published model exists but its data is invalid for route calculation. Publication should prevent this state; this response is defensive.

Error bodies contain `code` and `message`: `INVALID_ROUTE_REQUEST` for `400`, `ROUTE_NOT_FOUND` for `404`, and `INVALID_PUBLISHED_MODEL` for `422`. A foreign-site node produces `400` only when it belongs to another site's current published version; an unknown or unpublished node produces `404`.

Example `200` response:

```json
{
  "siteId": "00000000-0000-4000-8000-000000000001",
  "modelVersionId": "00000000-0000-4000-8000-000000000002",
  "totalDistanceMeters": 5.0,
  "totalCost": 10.0,
  "nodes": [
    {"nodeId": "00000000-0000-4000-8000-000000000010", "floorId": "00000000-0000-4000-8000-000000000020", "planVersionId": "00000000-0000-4000-8000-000000000030", "x": 1.0, "y": 2.0, "role": "ACCESS"},
    {"nodeId": "00000000-0000-4000-8000-000000000011", "floorId": "00000000-0000-4000-8000-000000000021", "planVersionId": "00000000-0000-4000-8000-000000000031", "x": 2.0, "y": 2.0, "role": "DESTINATION"}
  ],
  "links": [
    {"linkId": "00000000-0000-4000-8000-000000000040", "fromNodeId": "00000000-0000-4000-8000-000000000010", "toNodeId": "00000000-0000-4000-8000-000000000011", "type": "ELEVATOR", "distanceMeters": 5.0, "weight": 7.0, "verticalPenalty": 3.0, "accessibility": "ACCESSIBLE"}
  ],
  "verticalSections": [
    {"linkIndex": 0, "linkId": "00000000-0000-4000-8000-000000000040", "fromNodeId": "00000000-0000-4000-8000-000000000010", "toNodeId": "00000000-0000-4000-8000-000000000011", "fromFloorId": "00000000-0000-4000-8000-000000000020", "toFloorId": "00000000-0000-4000-8000-000000000021", "type": "ELEVATOR", "distanceMeters": 5.0, "cost": 10.0}
  ]
}
```

The endpoint is enabled by default for loopback-only local review; the feature flag `rumbo.navigation.routes.enabled` can disable it. A `CLINIC_ADMIN` JWT and site ownership are required (`401` without authentication, `403` without permission). Obstacle validation remains pending. The Sprint Backlog shows a global `/api/v1/routes?from&to`; the team accepted the site-scoped path in ADR 001. The TS-13 latency test uses a published synthetic grid of 300 nodes and 565 links, 10 warm-ups and 30 timed MockMvc requests through PostgreSQL 16. In the local `clean verify` run of 2026-11-04 before IAM integration, p95 was 16 ms and the maximum was 17 ms. That measurement is historical and does not establish latency after this merge. MockMvc omits network transit.

### Current provisional endpoint

`POST /api/v1/navigations/route` takes `siteId`, origin and destination floor/node IDs, and `accessibleOnly` in a JSON body. It uses Dijkstra over `demo` data and returns `totalDistanceMeters`, `nodes[]` and `links[]`; validation errors map to `422`. This endpoint remains provisional and separate from the published PostgreSQL route API. No compatibility requirement is assumed for clients that have not yet been integrated.

## Clinic, site and floor administration (US-06 / PR-101)

All operations require a `CLINIC_ADMIN` JWT. The authenticated account supplies
the ownership scope. Clients cannot choose the owner or link another account's clinic.

| Method and path | Request | Success |
| --- | --- | --- |
| GET `/api/v1/clinics/me` | — | 200 with the clinic profile, or 404 before registration |
| PUT `/api/v1/clinics/me` | `name`, `code`, `contactEmail`, optional `phone` | 200 with the saved profile and its stable ID |
| GET `/api/v1/sites` | — | 200 with the account's sites |
| POST `/api/v1/sites` | `name`, optional `address` and `allowDuplicateAddress` | 201 with the site |
| PUT `/api/v1/sites/{siteId}` | Same fields as POST | 200 with the updated site |
| GET `/api/v1/sites/{siteId}/floors` | — | 200 with registered floors ordered by level |
| POST `/api/v1/sites/{siteId}/floors` | Integer `number`, `name`, optional `description` | 201 with the floor and its server-generated ID |

The site response contains `id`, `name`, `address` and `clinicId`. The API retains
support for name-only site requests. Registering a clinic links previously
registered sites owned by that same account. Spatial floor ownership remains in
`planning_floor_sites`; the administrative floor stores the number and name
for that floor ID. Existing map floor identities remain valid.

An address matching another site in the same account returns 409
`SITE_ADDRESS_EXISTS` unless explicitly acknowledged. Duplicate floor levels
return 409 `FLOOR_NUMBER_EXISTS`. Invalid fields return 400, unauthenticated
requests return 401, other roles return 403, and foreign or missing sites return 404.

## Shared rules

- HTTP error bodies contain `code` and `message`; IAM, Planning beacon inventory and Analytics also
  return RFC 7807 `detail` with the same text. Planning graph validation
  additionally includes `issues[]` with element-specific details. Shared HTTP failures
  use `INVALID_REQUEST` (400), `RESOURCE_NOT_FOUND` (404), `METHOD_NOT_ALLOWED`
  (405), or `UNSUPPORTED_MEDIA_TYPE` (415); bounded contexts keep their own
  error codes for domain failures. The provisional demo route uses
  `INVALID_DEMO_ROUTE` (422) instead of its former `error` field.
- `navigation` only reads `PUBLISHED` models and never changes `planning` drafts or resources.
- An accessibility preference must not be bypassed to fabricate a route.
- Cross-floor links must be explicit vertical connectors in the published graph.
- Clients pass node identifiers, not destination display names, when requesting routes.
- `demo` fixtures are not a production data source.
