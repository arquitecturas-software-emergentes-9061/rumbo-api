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

## Architectural floor-plan files (TS-02 / PR-106)

Planning keeps one sequence of immutable original files per registered floor. The highest
number is the latest uploaded file. Uploading an original does not create or change a
`DRAFT`, `PUBLISHED` or `ARCHIVED` indoor graph, a coordinate space or a calibration.

### Register an original

`POST /api/v1/sites/{siteId}/floors/{floorId}/plans`

Send `multipart/form-data` with one part named `file`. An authenticated `CLINIC_ADMIN`
must own the site. The floor must already belong to that site in Planning's canonical
floor registry. The API never registers an unknown floor as a side effect of uploading.

- **201 Created** with `Location: /api/v1/sites/{siteId}/floors/{floorId}/plans/versions/{versionId}`
  and JSON fields `id`, `siteId`, `floorId`, `version`, `fileName`, `mediaType`, `sizeBytes` and `createdAt`.
- **400 Bad Request** for malformed IDs, missing part (`PLAN_FILE_REQUIRED`), empty file
  (`PLAN_EMPTY_FILE`) or unsafe file name (`PLAN_INVALID_NAME`).
- **401 / 403** for missing credentials or an account without the administrative role.
- **404 Not Found** (`FLOOR_PLAN_NOT_FOUND`) for an absent or foreign site or a floor outside that site.
- **413 Payload Too Large** (`PLAN_FILE_TOO_LARGE`) above 25 MiB per file or the 26 MiB
  multipart request limit.
- **415 Unsupported Media Type** for an unsupported extension, a conflicting declared media type
  (`PLAN_UNSUPPORTED_TYPE`) or a request that is not multipart.
- **422 Unprocessable Entity** (`PLAN_INVALID_CONTENT`) for unreadable or invalid contents.

Accepted originals are single-page unencrypted **PDF**, standalone **SVG** with an SVG
root and no DOCTYPE, **PNG**, and **JPG/JPEG**. Raster dimensions must be at most
8192 × 8192. The extension and declared media type must agree when the client supplies a
specific type. The backend always validates the actual file contents. A rejected upload
leaves all stored versions unchanged. Concurrent uploads to the same floor receive
distinct consecutive numbers. Each accepted upload creates a new version.

### Recover one specific original

`GET /api/v1/sites/{siteId}/floors/{floorId}/plans/versions/{versionId}`

Returns **200** with the stored bytes, original media type and attachment filename.
The response is private and non-cacheable. The same role and ownership rules apply.
An unknown or out-of-scope version returns **404**.

For calibratable originals, the response exposes the `X-Plan-Width` and
`X-Plan-Height` headers. They describe the top-left coordinate space used by
calibration. Raster files use pixels, SVG uses its display dimensions, and PDF uses
the rotated crop box in page units. The headers are available to allowed browser origins.

### List original file versions (TS-21 / PR-123)

`GET /api/v1/sites/{siteId}/floors/{floorId}/plans`

Returns **200** with an array of the same metadata as the upload response, sorted by
version descending. The first item is the current original. A registered floor without
uploads returns `[]`. File bytes are retrieved only through the version download endpoint.
A missing or foreign site returns **404** with `SITE_NOT_FOUND`. A missing floor or a
floor outside the owned site returns **404** with `FLOOR_NOT_FOUND`. The same role and
authentication requirements apply.

### Calibrate an original file (US-07 / PR-111)

`PUT /api/v1/sites/{siteId}/plan-calibrations/{planVersionId}` accepts the original file's
version ID and the existing request fields `floorId`, `unit`, `measuredDistance`,
`startX`, `startY`, `endX` and `endY`. Points must be distinct and inside that
file's dimensions. The response includes `metersPerUnit`. The matching `GET` retrieves
the saved measurement for that version or returns `CALIBRATION_NOT_FOUND`.
The original file's calibration is independent of graph publication. Draft graph
calibration keeps its existing behavior.

### Inspect a stored plan (US-08 / PR-127)

`POST /api/v1/sites/{siteId}/floors/{floorId}/plans/versions/{versionId}/interpretation`
uses the original scoped to the administrator, site and floor. `SVG_CANDIDATES`
includes candidate walls, zones, doors and links in the original preview coordinate frame.
Annotated SVG is interpreted directly. When the optional structural mapper is enabled, PDF,
PNG, JPEG and unannotated SVG can also produce candidates. Files without a usable proposal
remain `PREVIEW_ONLY`; the original remains available for viewing and calibration.

Structural mapper v4 proposals add `zones[].polygon` and `zones[].labelPosition` alongside
the compatible bounding-box fields. A polygon is `{outer: [{x,y}, ...], holes: [[{x,y}, ...], ...]}`.
`barriers[]` contains `{kind: WALL|WINDOW|OBSTACLE, polygon}` and `walkable[]` contains the
traversable polygons. Door endpoints retain their actual orientation. Clients may fall back
to rectangular zones and `wallCandidates` for older mapper responses or annotated SVG.
These additions describe the proposal. The editor may adopt compatible wall candidates and
door openings into the versioned draft geometry described below; the original SVG is unchanged.

Route candidates are reviewed against recognized walls, door openings, barriers and traversable
spaces before they appear in `connections[]`. A crossing at a confirmed opening uses `DOORWAY`;
a crossing through a wall or barrier, a doorway without an associated opening, or a route outside
confirmed traversable space is omitted from the candidate graph with a `warnings[]` explanation.
Low-confidence or unmatched doors and conflicting walls remain in `doors[]` and
`wallCandidates[]` for administrator review. Missing or incomplete geometry does not authorize
the interpreter to invent a navigable route.
Door candidates detected by the structural mapper remain in `doors[]` even when their adjacent
spaces cannot be identified; these receive a warning and do not authorize `DOORWAY` links. A
proposal with reviewable doors or walls may have no recognized zones. An SVG with explicit rooms
but no annotated doors can receive structural door candidates without replacing its explicit rooms.

The result contains `CONFIRMED` with `metersPerUnit` only when that version and floor
have a saved physical reference. `PROVISIONAL` keeps all dimensions in plan units.
SVGs outside the interpreter's supported geometry return `422 INVALID_SVG_PLAN`.
The response is a proposal for review. Graph writes use the separate revision-checked
draft contract and require an explicit mapping between the proposal and a draft plan.

### Storage and rollout

The original bytes and metadata commit together in PostgreSQL (`BYTEA`) with no external
storage service. The size cap bounds each upload and the existing floor registry provides
the site association. When PR-101 is deployed, its floor registration uses that same
registry and the site's clinic relationship applies without a second ownership table.
Deploy PR-101's migration `202611051700` before PR-106's `202611051800` on shared
databases so Flyway applies both in order. PDFBox validates PDF structure and page count
without rendering or extracting embedded files.

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

`GET /api/v1/sites/{siteId}/maps/draft/graph` discovers the single current draft for
an owned site. It returns the same graph snapshot and ETag as a version-specific GET.
An owned site without a draft receives `404`. Subsequent writes use the map and version
IDs from this response and retain the existing `If-Match` requirement.

`GET /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph`

`PUT /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph`

Both operations address an **existing DRAFT** version; they do not create a map, version, floor plan, or scale. The `GET` response contains `siteId`, `mapId`, `modelVersionId`, `status`, `plans[]` with coordinate bounds and optional `metersPerUnit`, `nodes[]`, `connections[]`, `destinations[]`, `walls[]`, and `doors[]`. `PUT` replaces nodes, connections, directory destinations, walls, and doors atomically, preserving registered plans and confirmed scales. The request must explicitly contain `nodes`, `connections`, and `destinations`. Geometry-aware editors also send `walls` and `doors`; omission of either geometry collection preserves its stored value for older clients. An explicit empty array clears that collection. A wall has stable `id`, `floorId`, `planVersionId`, and two plan-unit endpoints (`x1`, `y1`, `x2`, `y2`). A door has stable `id`, `wallId`, and opening fractions `from` and `to` along its wall. Walls must fit their registered plan and doors must refer to a wall without overlapping openings. A same-floor arista crossing a wall must pass through a door and have type `DOORWAY`; otherwise the replacement is rejected with `422` and the draft remains unchanged. Each directory entry has `id`, `nodeId`, `type` (`DOCTOR`, `SPECIALTY`, or `SERVICE`), `name`, and optional `specialty`. Its node must exist in that version with type `DESTINATION`. A connection may include `verticalPenalty`, a nonnegative routing cost added to `weight` when it crosses floors; omission means `0`, and a nonzero value on a same-floor connection is rejected. `accessibility` is optional (`UNKNOWN`, `ACCESSIBLE`, or `INACCESSIBLE`), defaults to `UNKNOWN`, and is returned by `GET`. An empty collection is allowed while editing; publication still requires a valid navigable graph. The endpoint is enabled by default for loopback-only local review; the feature flag `rumbo.planning.editing.enabled` can disable it.

When a plan has editable walls, a `DOORWAY` connection must cross or end at a door opening on one of those walls. A recognized wall or door that cannot be associated safely stays visible in the editor for review; it is not silently removed from the interpretation preview. The editor blocks saving a graph with unresolved wall crossings.

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

Error bodies contain `code` and `message`: `INVALID_ROUTE_REQUEST` for `400`, `ROUTE_NOT_FOUND` for `404` when a node or published model is missing or no route exists even without the accessibility restriction, `NO_COMPATIBLE_ROUTE` for `404` when `accessibleOnly=true` and only an unrestricted route exists (message: "No route satisfies the requested accessibility preference"; the incompatible route is never returned), and `INVALID_PUBLISHED_MODEL` for `422`. A foreign-site node produces `400` only when it belongs to another site's current published version; an unknown or unpublished node produces `404`.

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

## Personal profile

`GET /api/v1/profiles/me` reads the authenticated caller's stored profile. The user ID
comes exclusively from the JWT. No request body or client-supplied user ID is required.

- `200 OK`: the same `ProfileResponse` as `PUT /api/v1/profiles/me`, containing
  `userId`, `fullName`, `role`, nullable `phoneNumber`, and nullable `imageUrl`.
  The response never includes `storageKey`. Later sign-ins retrieve the latest saved data.
- `401 Unauthorized`: no valid authentication token.
- `404 Not Found`: the authenticated account has no profile; `code: "PROFILE_NOT_FOUND"`.

## Subscription checkout and payment orders (PR-79 v3)

Both endpoints require a `CLINIC_ADMIN` JWT. The payer and contracting clinic are
resolved from that JWT; clients cannot supply an owner or organization ID.

`POST /api/v1/subscriptions/checkout-sessions`

```json
{
  "planCode": "BASIC",
  "billing": {
    "legalName": "Clínica Ejemplo S.A.C.",
    "ruc": "20491823901",
    "fiscalAddress": "Av. Javier Prado Este 4200, Lima",
    "billingEmail": "pagos@clinica.pe",
    "purchaseOrder": "PO-2024-089"
  }
}
```

All billing strings are trimmed before validation and storage. `billing` is
required. `legalName` requires 2–160 characters, `ruc` exactly 11 digits (no
prefix or checksum validation), `fiscalAddress` 5–200 characters, and
`billingEmail` a valid email address of at most 254 characters. `purchaseOrder`
is optional, at most 40 characters; omission, null or whitespace becomes null.

Invalid billing returns `400 VALIDATION_FAILED` in the existing Problem Details
format, with `errors` keyed by `billing.legalName`, `billing.ruc`,
`billing.fiscalAddress`, `billing.billingEmail`, or `billing.purchaseOrder`.
A missing or null billing object uses `billing`. Validation happens before any
order is saved or the payment gateway is called.

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Invalid request fields",
  "errors": { "billing.ruc": "Must contain exactly 11 digits" }
}
```

On success, `201 Created` includes a `Location` for the payment order:

```json
{
  "orderId": "00000000-0000-4000-8000-000000000001",
  "organization": { "id": "00000000-0000-4000-8000-000000000002", "name": "Clínica Ejemplo" },
  "planCode": "BASIC",
  "status": "PENDING",
  "amount": 39.90,
  "currency": "PEN",
  "checkoutUrl": "https://checkout.stripe.com/...",
  "billing": {
    "legalName": "Clínica Ejemplo S.A.C.",
    "ruc": "20491823901",
    "fiscalAddress": "Av. Javier Prado Este 4200, Lima",
    "billingEmail": "pagos@clinica.pe",
    "purchaseOrder": "PO-2024-089"
  }
}
```

After request validation, an inactive or missing plan returns `404 PLAN_NOT_FOUND`,
Enterprise returns `409 PLAN_REQUIRES_SALES_CONTACT`, and an administrator without
a registered clinic returns `409 ORGANIZATION_REQUIRED`. Provider failures retain
the existing `503 PAYMENTS_UNAVAILABLE` / `502 PAYMENT_PROVIDER_ERROR` behavior.

`GET /api/v1/subscriptions/payment-orders/{orderId}` returns `200` only to the
order's owner; foreign or missing orders return `404 PAYMENT_ORDER_NOT_FOUND`.
Its response includes `orderId`, `organization`, `planCode`, `status`, `amount`,
`currency`, `createdAt`, `billing` with the same five fields, and `paidAt`.
It does not include `checkoutUrl`. `paidAt` is null until payment confirmation;
the signed completed-payment webhook stores the confirmation instant and repeated
events preserve it. For example, after payment:

```json
{
  "orderId": "00000000-0000-4000-8000-000000000001",
  "organization": { "id": "00000000-0000-4000-8000-000000000002", "name": "Clínica Ejemplo" },
  "planCode": "BASIC",
  "status": "PAID",
  "amount": 39.90,
  "currency": "PEN",
  "createdAt": "2026-11-12T16:00:00Z",
  "paidAt": "2026-11-12T16:05:00Z",
  "billing": {
    "legalName": "Clínica Ejemplo S.A.C.",
    "ruc": "20491823901",
    "fiscalAddress": "Av. Javier Prado Este 4200, Lima",
    "billingEmail": "pagos@clinica.pe",
    "purchaseOrder": null
  }
}
```

Billing is an immutable snapshot on the payment order. Historical orders may
return `billing: null` and `paidAt: null`; the migration does not invent past data.
This flow does not issue a fiscal receipt, assign fiscal numbering, or generate a PDF.

## Visitor route preferences (PR-68)

Both endpoints require a JWT; the user ID comes exclusively from that token.

| Method and path | Request | Success |
| --- | --- | --- |
| GET `/api/v1/profiles/me/preferences` | No body | 200 with stored preferences |
| PUT `/api/v1/profiles/me/preferences` | Required boolean fields `avoidStairs`, `simplerRoute`, `voiceGuidance` | 200 with saved preferences |

Both responses contain the three booleans and `configured`. Defaults are provisionally
all `false`: the design shows example states, not defaults. Initially `configured` is
`false`; the first and every subsequent PUT records `preferences_updated_at`, so it
becomes `true`, even when all submitted values are false. Values persist across sign-ins.
`GET /api/v1/profiles/me` also includes `preferences` with the three booleans,
alongside all existing profile fields.

Missing or null fields return 400 `VALIDATION_FAILED` with `errors.<field>`.
Invalid types such as `"avoidStairs":"yes"` return 400 `MALFORMED_REQUEST`.
Invalid requests do not change the saved row. Both endpoints return 401 without
a valid token and 404 `PROFILE_NOT_FOUND` if the caller has no profile.

Only `avoidStairs` currently affects route calculation: the client sends
`accessibleOnly=true` to `GET /api/v1/sites/{siteId}/routes`; no compatible route
returns 404 `NO_COMPATIBLE_ROUTE` (PR-71). `simplerRoute` is stored but navigation
does not apply it because no cost criterion exists. `voiceGuidance` controls client TTS.
Navigation is unchanged.

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

### Internal payment receipt

The payment order response includes a nullable receipt. A paid order with a recorded
payment timestamp returns the receipt number (RA- followed by the order UUID) and
issuedAt (the recorded payment timestamp). The order supplies billing, plan, amount
and currency. The number is stable across repeat requests and webhook retries.
Unpaid orders and historical orders without a payment timestamp return a null receipt.
This receipt is an internal payment record and has no fiscal validity.

## Plan quotas (PR-91)

The plan catalog includes quota and the current subscription includes plan.quota.
Each limit is a total per clinic. Configure limits under
rumbo.subscriptions.quotas.<PLAN>.sites, .floors and .beacons.
An empty property and its JSON null represent an unlimited resource.

## Navigation heatmap

GET /api/v1/analytics/navigation-heatmap?siteId=<uuid>&from=<instant>&to=<instant>
requires the CLINIC_ADMIN who owns the site. It uses the same date validation and
half-open interval as the navigation summary. Returns cells with floorId,
nodeReference, deviations and recalculations as absolute counts, plus
discardedEvents for relevant events without a floor or node.
The provisional WA14/WA14B defaults require team confirmation: BASIC has 1 site,
3 floors and 15 beacons; PROFESSIONAL has 3 sites, 10 floors and 50 beacons;
ENTERPRISE has unlimited resources.

## Subscription usage (PR-94)

`GET /api/v1/subscriptions/me` includes `usage` for the authenticated owner,
alongside `plan.quota`, the effective status and the current period:

```json
"usage": {
  "sites": {"used": 1, "limit": 1},
  "floors": {"used": 2, "limit": 3},
  "beacons": {"used": 4, "limit": 15}
}
```

Counters include all of that owner's sites, floors and beacons across sites;
another owner's resources are excluded. An owner without a clinic has zero usage.
Each `limit` comes from the subscription plan's configured quota; JSON `null`
means unlimited, including all three limits for ENTERPRISE. Usage remains present
when the effective status is `EXPIRED`, so clients can still display consumption
in restricted mode. These are current stored counts, not a historical snapshot.
`GET /api/v1/subscriptions/plans` continues to include `quota` for each plan.

## Registration quotas (PR-93)

Site, floor and beacon registrations count the authenticated owner's total usage
across sites before inserting. Concurrent registrations for that owner are serialized.
An absent, expired or cancelled subscription returns HTTP 409 with code
`SUBSCRIPTION_REQUIRED` and message
`An active subscription is required to register sites, floors or beacons`.
Requiring a subscription even for the first registration is a provisional assumption
consistent with WA14B restricted mode. The exact period end remains active,
matching the current subscription query; instants after it are expired.
An exhausted quota returns HTTP 409 with code `PLAN_QUOTA_EXCEEDED`, integer
`limit` and message `Your plan allows at most N sites` (or `floors` / `beacons`).
Rejected registrations insert no row. Reads, edits and deletions retain their behavior.
An empty property and its JSON null represent an unlimited resource.
