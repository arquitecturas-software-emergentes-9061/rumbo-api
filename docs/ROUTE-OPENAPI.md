# PR-224 / PR-229 — Real route contract and regression tests

The generated OpenAPI is available under `GET /v3/api-docs` and Swagger UI
`/swagger-ui/index.html`.

### Published Planning A* (not the demo engine)

`GET /api/v1/sites/{siteId}/routes?from={nodeId}&to={nodeId}&accessibleOnly=false`.

Path/query IDs are UUID. Authentication: `Authorization: Bearer <JWT>`,
role `CLINIC_ADMIN`, and ownership of `siteId` required.
This feature needs `postgres` and `RUMBO_NAVIGATION_ROUTES_ENABLED=true`.

Response 200 has `siteId`, `modelVersionId`, `totalDistanceMeters`,
`totalCost`, ordered `nodes[]` and ordered `links[]` with
`linkId,fromNodeId,toNodeId,kind,distanceMeters,weight,verticalPenalty,cost`.
Costs and physical lengths are **different measurements**.

Errors: 400 invalid or unknown node, 401 missing credentials, 403 wrong site,
404 absent published graph/disconnected path, 422 unsupported accessibility filter.

The persisted model does not yet contain verified accessibility flags or obstacle
state, so `accessibleOnly=true` fails closed with
`ACCESSIBILITY_UNAVAILABLE`, rather than implying the route is accessible.

Evidence: `AStarRouteCalculationDomainServiceTest` validates directed weighted
routes and compares cost against Bellman-Ford; `PublishedRouteControllerIntegrationTest`
inserts and reads a real two-floor published graph in PostgreSQL, verifies
elevator selection with vertical penalties and site scoping; `NavigationOpenApiContractTest`
continues covering the demo contract.

### Backward-compatible demonstration

The `demo` profile retains `POST /api/v1/navigations/route`,
a Dijkstra implementation with a different payload. This is *not* the target
published-model endpoint.
