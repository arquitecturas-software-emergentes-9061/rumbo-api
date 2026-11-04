# PR-224 / PR-229 — Real route contract and regression tests

The generated OpenAPI is available under `GET /v3/api-docs` and Swagger UI
`/swagger-ui/index.html`.

### Published Planning A* (not the demo engine)

`GET /api/v1/sites/{siteId}/routes?from={nodeId}&to={nodeId}&accessibleOnly=false`.

Path/query IDs are UUID. Authentication: `Authorization: Bearer <JWT>`,
role `CLINIC_ADMIN`, and ownership of `siteId` required.
This feature needs `postgres`; `rumbo.navigation.routes.enabled` is true by default.

Response 200 has `siteId`, `modelVersionId`, `totalDistanceMeters`,
`totalCost`, ordered `nodes[]`, ordered `links[]`, and `verticalSections[]`.
Links include `linkId,fromNodeId,toNodeId,type,distanceMeters,weight,verticalPenalty,accessibility`.
Costs and physical lengths are **different measurements**.

Errors: 400 invalid parameters or a node from another site's published model,
401 missing credentials, 403 wrong site, 404 unknown node, absent published graph
or disconnected path, and 422 invalid published model.

The persisted model contains declared `UNKNOWN`, `ACCESSIBLE`, and `INACCESSIBLE`
connection states. `accessibleOnly=true` traverses only declared `ACCESSIBLE`
connections. Obstacle clearance is not yet modeled or verified.

Evidence: `AStarRouteCalculationDomainServiceTest` validates directed weighted
routes and compares cost against Bellman-Ford; `PublishedRoutesControllerIntegrationTest`
inserts and reads a real two-floor published graph in PostgreSQL, verifies
elevator selection with vertical penalties and site scoping; `NavigationOpenApiContractTest`
continues covering the demo contract. `SiteAuthorizationIntegrationTest` verifies
the real JWT and site-owner chain for Planning operations.

### Backward-compatible demonstration

The `demo` profile retains `POST /api/v1/navigations/route`,
a Dijkstra implementation with a different payload. This is *not* the target
published-model endpoint.
