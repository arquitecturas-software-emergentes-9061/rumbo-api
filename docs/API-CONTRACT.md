# API contracts · Navigation BC · proposed v0.1

All examples assume a published site model and an authorized consumer. Demo fixtures
are **not** a production datasource. Existing mobile/web clients must map node identifiers,
not destination display names, to routing requests.

## GET /api/v1/navigations/destinations
Query: `siteId` (UUID required), `query` (optional), `accessibleOnly` (optional bool).
Searches destination name + specialty + category, case- and accent-insensitive,
with all query words required. Only returns destinations from that site.

## POST /api/v1/navigations/route
```json
{"siteId":"<uuid>","startFloorId":"<uuid>","startNodeId":"<uuid>",
 "destinationFloorId":"<uuid>","destinationNodeId":"<uuid>","accessibleOnly":false}
```
Response 200: `totalDistanceMeters`, `nodes[]`, `links[]`.
Response 422: invalid model, inaccessible/unreachable route, unknown node.

## Boundaries/constraints
- No mutation of the published model from this BC.
- No bypass of accessibility restrictions when requested.
- Strict wall intersections block edges; endpoints may touch, per MVP policy.
- Cross-floor links must be explicit vertical connectors in the published graph.
- User permissions and authenticated site scoping are required before production use.
- No path may be fabricated where the published graph has no connection.
