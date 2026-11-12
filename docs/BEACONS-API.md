# BLE inventory — Planning bounded context

Planning owns sites, floor plans, and the registered beacon inventory. The service
stores beacon references in PostgreSQL. The existing `positioning_beacons` table
name is retained for migration compatibility; it does not define a bounded context.
It does **not**
scan real radios, claim RSSI measurements, or calibrate hardware.

All endpoints need an IAM JWT and `CLINIC_ADMIN` role. Accounts own sites
through `indoor_sites.owner_user_id`; a nonowner cannot read or update their data.
Multi-admin clinic tenancy will require a separate membership model.

Endpoints:
- `POST /api/v1/sites` — creates an owned site, `{"name":"Centro Médico"}`.
- `GET /api/v1/sites` — lists sites owned by the caller.
- `POST /api/v1/sites/{siteId}/beacons` — registers beacon
  `{"floorId":"<uuid>","identifier":"AA:BB:CC:DD","x":10.5,"y":7}`.
  The coordinates must be within a DRAFT or PUBLISHED plan of that site/floor;
  invalid references return 422 `INVALID_SPATIAL_ANCHOR`.
  Optional metadata: `name`, `proximityUuid` + `major` + `minor` (0-65535, all three together),
  `txPowerDbm` (-120..0). `identifier` is optional when the UUID/major/minor triplet is sent
  and is then derived as `uuid:major:minor`. `x`/`y` are optional: a beacon without them is
  registered **unplaced and inactive**, and an unplaced beacon cannot be activated (422
  `INVALID_BEACON`). Both the normalized identifier and physical iBeacon triplet are unique
  across all sites, including inactive inventory (409 `BEACON_IDENTIFIER_EXISTS`).
- `PUT /api/v1/sites/{siteId}/beacons/{id}` — replaces the editable fields with the same
  body. Removing the position deactivates the beacon.
- `GET /api/v1/sites/{siteId}/beacons?floorId=<uuid>` — inventory by floor.
- `GET /api/v1/sites/{siteId}/beacons/{id}` — one beacon.
- `PATCH /api/v1/sites/{siteId}/beacons/{id}/status` — activate/deactivate.

The spatial reference uses plan coordinates, not GPS or meters. The identifier
is normalized to lowercase and unique across the platform. Tests cover duplicate,
wrong-account and nonexistent floor/point cases.

The Flyway migration checks existing inventory before changing any row. If two sites
already use the same normalized identifier or iBeacon triplet, deployment stops so an
administrator can resolve the ambiguity without silently deleting a beacon.

Not included: BLE signal sampling, Android runtime permissions, beacon calibration,
automatic retargeting after reimporting a floor plan, or institutional multi-admin
tenancy.
