# Rumbo API — Planning and Navigation contract

Status: agreed target contract for future iterations. Sections marked **target** are not implemented yet. See [ADR 001](decisions/001-architecture-and-contract.md) for the decisions behind this contract.

## Published indoor model — target

`planning` owns the editable spatial model and publishes a read-only snapshot for `navigation`. The exchange contract, through a port between modules and a versioned API when exposed, must contain:

- `siteId` and `modelVersionId` for a `PUBLISHED` version.
- Floors, coordinate system, units and scale needed to convert visual distances to meters.
- Nodes with identifiers, floor, type and coordinates; connections with endpoints, direction, type, physical distance, persisted weight, accessibility restrictions and traversability state.
- Destinations linked to valid nodes and obstacles needed to validate traversable connections.

### Coordinate convention for Planning

Each floor-plan version defines an unscaled rectangular visual coordinate space with positive `widthUnits` and `heightUnits`. Its origin is the top-left corner: x increases rightward and y downward. Coordinates include `floorId` and `planVersionId`; they must be finite and inside the inclusive bounds `(0, 0)` to `(widthUnits, heightUnits)`. A rendered or resized image must transform those coordinates for display rather than replacing the stored plan coordinates.

A confirmed `MapScale` is tied to `floorId` and `planVersionId` and expresses `metersPerUnit = realDistanceMeters / EuclideanDistanceInPlanUnits` between two distinct reference points of that version. Physical connection distance is expressed in meters; routing weight is a separate positive cost. A plan without a confirmed physical reference has no confirmed scale. `planning` now stores and reloads these draft values with nodes and connections in PostgreSQL. Publishing and serving a read-only model remain future work.

Reads never return drafts. Publishing a new version must be atomic so a route uses one coherent version. Authorization of the consumer and site is required before serving real models; it is not implemented yet.

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
