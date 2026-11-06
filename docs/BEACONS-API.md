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
- `GET /api/v1/sites/{siteId}/beacons?floorId=<uuid>` — inventory by floor.
- `GET /api/v1/sites/{siteId}/beacons/{id}` — one beacon.
- `PATCH /api/v1/sites/{siteId}/beacons/{id}/status` — activate/deactivate.

The spatial reference uses plan coordinates, not GPS or meters. The identifier
is trimmed, normalized to lowercase and unique across all sites, including
inactive inventory. A duplicate returns HTTP 409 with `BEACON_IDENTIFIER_EXISTS`.
The response omits any details about the existing registration.

Before deploying Flyway migration `202611061000` on a shared database, apply the
required earlier migrations in version order. The migration checks normalized
identifiers for collisions and aborts the transaction if any conflict exists.
Review and resolve conflicts explicitly before retrying. To list candidate groups
within the authorized database administration session, use:

```sql
SELECT lower(btrim(identifier, E' \t\r\n\f' || chr(11)) COLLATE "C") AS canonical_identifier,
       count(*) AS registrations
FROM positioning_beacons
GROUP BY 1
HAVING count(*) > 1;
```

Not included: BLE signal sampling, Android runtime permissions, beacon calibration,
automatic retargeting after reimporting a floor plan, or institutional multi-admin
tenancy.
