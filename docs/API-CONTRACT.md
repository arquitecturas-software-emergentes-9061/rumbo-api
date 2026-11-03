# RUMBO API — current Planning, Positioning and Navigation contracts

These contracts describe the integration branch for review before it is merged to `develop`.
All persistent APIs use PostgreSQL and JWT-authenticated `CLINIC_ADMIN` site ownership.
Site membership is currently tied to a single IAM account and `indoor_sites`, not to a
multi-administrator organization.

## Spatial model and authoring

Planning owns a site-scoped indoor map and versioned floor coordinate systems, candidate
nodes, connections and destinations. A model has DRAFT/PUBLISHED/ARCHIVED versions.
The Planning endpoints below require the `postgres` profile and explicit feature flags:
`RUMBO_PLANNING_EDITING_ENABLED=true` and
`RUMBO_PLANNING_PUBLICATION_ENABLED=true`.
They also enforce authenticated site ownership.

- `GET /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph`:
  reads an existing DRAFT graph.
- `PUT` to the same URL replaces nodes, links and destinations atomically while
  preserving the plan coordinate spaces and confirmed scales.
- `PATCH /api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}`:
  body `{"status":"PUBLISHED"}`, content type `application/merge-patch+json`.
  Validates and publishes the draft atomically.

The scale reference API `PUT/GET /api/v1/sites/{siteId}/plan-calibrations/{planVersionId}`
persists physical measurement provenance (unit, distance, 2 plan coordinates)
and applies `meters_per_unit` to the real, matching *DRAFT* coordinate space in the
**same transaction**. Missing, archived, published or wrong-site plans are rejected.
Map version ownership and plan coordinates are validated before mutation.

## BLE inventory (PR-192, PR-194)

`POST /api/v1/sites/{siteId}/beacons` requires a site/floor and coordinates within
an existing DRAFT or PUBLISHED floor plan of the site. Invalid floor references and
coordinates outside plan bounds return 422 `INVALID_SPATIAL_ANCHOR`.

`GET /api/v1/sites/{siteId}/beacons?floorId={uuid}` returns spatial beacon references
for the requested floor; without a floor filter, returns all beacons in the site.
A beacon location is a plan coordinate, not necessarily a navigation-graph node.
A nonexistent floor *filter* yields an empty inventory list; it does not authorize
registering a beacon on an unknown floor.

## Routing (PR-224, PR-229)

`GET /api/v1/sites/{siteId}/routes?from={nodeId}&to={nodeId}&accessibleOnly=false`

Available in the `postgres` profile when `RUMBO_NAVIGATION_ROUTES_ENABLED=true`,
and protected by authenticated site-owner authorization. The route is always
computed on the **current PUBLISHED** spatial graph; drafts are never exposed.

- `200`: `siteId`, `modelVersionId`, `totalDistanceMeters`, `totalCost`,
  ordered `nodes[]` and `links[]`. Each link includes physical distance,
  base weight, vertical penalty and resulting cost.
- `400`: invalid/unknown node.
- `401/403`: missing authentication or unauthorized site.
- `404`: missing published model or disconnected route.
- `422 ACCESSIBILITY_UNAVAILABLE`: `accessibleOnly=true` requested while the
  persisted graph lacks verified accessibility metadata. The API **fails closed**.

A* minimizes `weight + verticalPenalty`, follows directed links and treats
STAIRS/RAMP/ELEVATOR as the only valid inter-floor edges.
No disconnected graph can fabricate a route. Tests compare expected weighted
costs across floors, verify direction and disconnected paths, and use Testcontainers
against a real published PostgreSQL model.

`GET /api/v1/sites/{siteId}/directory-entries?q={term}` is a published
medical directory (flag `RUMBO_NAVIGATION_DIRECTORY_ENABLED=true`).
All three production route/directory/map features are disabled by default until
configured for a reviewed deployment.

## Provisional demo-only endpoints

`GET /api/v1/navigations/destinations` and
`POST /api/v1/navigations/route` exist only under the `demo` profile.
They use Dijkstra and synthetic fixtures; their older response format is
not the live Planning A* contract. Do not expose demo routes in production.

## SVG interpretation (PR-142, PR-143, PR-159)

`POST /api/v1/plan-interpretations/svg` extracts safe straight-wall and label candidates.
`POST /api/v1/plan-interpretations/svg/topology` takes annotated rectangular rooms,
corridors, services and explicit door segments, and returns tentative nodes/links
to review. These endpoints do not automatically persist geometry or publish routes.
Unannotated raster scans/curved geometry need separate computer-vision/OCR work,
and uncertain door openings must not be guessed as traversable.
