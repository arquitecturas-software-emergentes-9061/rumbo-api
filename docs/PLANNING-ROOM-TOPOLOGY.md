# PR-142 / PR-143 / PR-159 — SVG room topology candidate

`POST /api/v1/plan-interpretations/svg/topology` uses the same payload as
`POST /api/v1/plan-interpretations/svg`: `{"svg":"<svg ...>...</svg>"}`.

Recognized source annotations:
- `<rect data-rumbo-role="room" x="0" y="0" width="10" height="10"/>`
- `<rect data-rumbo-role="corridor" .../>`
- `<rect data-rumbo-role="service" .../>`
- `<line data-rumbo-role="door" x1="10" y1="4" x2="10" y2="6"/>`
- `<text x="5" y="5">Consultorio 101</text>`

The response contains candidate `zones[]`, `doors[]`, `nodes[]`,
`connections[]`, `wallCandidates[]` and `warnings[]`. A node/connection
is proposed only when exactly two rectangular zones share an explicitly
marked door. Distances are in plan units, not physical meters.

The real editable graph is now available through the Planning draft API
(in the `postgres` profile, flag `RUMBO_PLANNING_EDITING_ENABLED=true`).
The client must convert reviewed candidate geometry to that API's
`GraphUpdateResource` and calibrate physical distances before publishing.
The parser itself **never publishes or modifies** an indoor map.

Unannotated PNG/JPEG/PDF images, uncertain door shapes, curved SVG paths and
barrier-free routing require separate research. A candidate link must not
be presented as a verified safe corridor without review.
