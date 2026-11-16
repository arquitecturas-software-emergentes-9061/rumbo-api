# PR-192 / PR-194 — Beacon inventory spatial validation

`GET /api/v1/sites/{siteId}/beacons?floorId=<uuid>` returns registered beacon
references, constrained to the authenticated clinic administrator's site.
The filter returns a list sorted by identifier. Nonexistent floor filters return [].

`POST /api/v1/sites/{siteId}/beacons` accepts
`{"floorId":"<uuid>","identifier":"aa:bb:cc","x":10,"y":5}`.
The registration is permitted only when the site owns a DRAFT/PUBLISHED map
with that floor and the finite, non-negative `(x,y)` falls inside its
persisted `plan_coordinate_spaces` bounds. An unregistered floor or outside
coordinate returns HTTP 422 / `INVALID_SPATIAL_ANCHOR`.

A physical BLE transmitter may be installed *between* navigation nodes.
Therefore the contract explicitly validates a **floor-plan coordinate**,
not equality to an existing graph node. The original Jira requirement
“rechazo de punto espacial inexistente” is interpreted as a location
that is absent from the registered floor's coordinate space.

Regression tests run on Testcontainers: successful registration and listing;
multi-floor isolation; duplicate IDs; malformed UUIDs; invalid coordinates;
unknown beacon IDs; invalid floor/point; wrong-account access.
Live BLE scanning, device signal quality and hardware calibration are separate Android tasks.

Existing registrations made before spatial validation should be audited when real
site maps are provisioned. The current model does not automatically migrate beacon
coordinates if a floor plan changes its coordinate frame.
