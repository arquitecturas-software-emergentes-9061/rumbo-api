# BLE inventory — Positioning bounded context

The service stores registered beacon references in PostgreSQL. It does **not**
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
- `GET /api/v1/sites/{siteId}/beacons?floorId=<uuid>` — inventory by floor.
- `GET /api/v1/sites/{siteId}/beacons/{id}` — one beacon.
- `PATCH /api/v1/sites/{siteId}/beacons/{id}/status` — activate/deactivate.

The spatial reference uses plan coordinates, not GPS or meters. The identifier
is normalized to lowercase and unique per site. Tests cover duplicate,
wrong-account and nonexistent floor/point cases.

Not included: BLE signal sampling, Android runtime permissions, beacon calibration,
automatic retargeting after reimporting a floor plan, or institutional multi-admin
tenancy.
