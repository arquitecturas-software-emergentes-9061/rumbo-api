# BLE inventory – positioning bounded context

The API stores *registered* beacon references. It does not claim to discover or calibrate BLE devices in the physical world. Native Android BLE scanning, RSSI sampling, real-world calibration and map ownership integration remain separate tickets.

## Security
All endpoints require an IAM bearer token with the `CLINIC_ADMIN` role.
The inventory creates UUIDv4 site identities scoped to the **authenticated account ID**. Every request verifies the site owner, returning 404 for cross-account references. Account scoping is an interim secure boundary: when the organization/clinic membership model is introduced, site ownership must be migrated to clinic-based membership before multi-admin collaboration. A floorId is a reference supplied by the caller; floor existence in Planning cannot yet be verified because its branch is not merged.

## Endpoints
- `POST /api/v1/sites` `{"name":"Centro Medico"}` → 201 `{id,name}`.
- `GET /api/v1/sites` → account's site list.
- `POST /api/v1/sites/{siteId}/beacons` `{"floorId":"<uuid>","identifier":"AA:BB:CC:DD","x":10.5,"y":7}` → 201 registered beacon, `active:true`.
- `GET /api/v1/sites/{siteId}/beacons[?floorId=<uuid>]` → site inventory, sorted by identifier.
- `GET /api/v1/sites/{siteId}/beacons/{id}` → beacon.
- `PATCH /api/v1/sites/{siteId}/beacons/{id}/status` `{"active":false}` → current beacon.

Coordinates use plan units of the referenced floor, not GPS coordinates or meters. Both must be finite, non-negative numbers; validation against calibrated floor dimensions must be enabled when Planning provides its read-only floor contract. Beacon identifiers are trimmed/lowercased, 4–100 ASCII alphanumeric/colon/underscore/hyphen characters, unique within a site.

## Tickets addressed / limitations
- Backend slice of US-14 (register/list beacon, active flag), TS-06 (inventory by floor, duplicate checks, API tests).
- Not yet delivered: detecting BLE radio signals (TS-08 mobile), reference-to-published-model validation, physical calibration, multi-admin clinic tenancy, Android UI and integrations with Planning's floor graph.
- This module uses JDBC inside a bounded context with PostgreSQL as source of truth, a timestamp-prefixed Flyway migration, Problem Details codes, and JUnit/Testcontainers integration tests.
