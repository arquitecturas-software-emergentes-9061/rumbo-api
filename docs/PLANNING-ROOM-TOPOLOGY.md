# PR-142 / PR-143 / PR-159 — SVG room topology candidate

The authenticated `POST /api/v1/plan-interpretations/svg/topology` endpoint accepts the same
`{"svg":"<svg ...>...</svg>"}` payload as the original SVG interpretation preview.

Use explicit semantic hints on the **source SVG**:
- `<rect data-rumbo-role="room" x="0" y="0" width="10" height="10"/>`
- `<rect data-rumbo-role="corridor" .../>`
- `<rect data-rumbo-role="service" .../>`
- `<line data-rumbo-role="door" x1="10" y1="4" x2="10" y2="6"/>`
- `<text x="5" y="5">Consultorio 101</text>`

Response: editable `zones[]` (label + category + bounds), `doors[]`, candidate `nodes[]`,
candidate `connections[]`, `wallCandidates[]`, and diagnostics `warnings[]`. Links only
exist when an explicitly marked door lies on the common boundary of **exactly two**
rectangular zones, and link lengths are in **plan units**, not meters.

The general SVG reader also extracts unmarked walls but **never reinterprets a
door as a blocking wall**. The system rejects transformed XML and invalid coordinates.
Unlabeled zones are given a tentative fallback category and warning. A door with
zero or >2 adjacent zones creates no connection.

**Important limits:** This is a conservative semi-automatic proposal, not
full raster/AI plan segmentation. No automatic door inference from a gap,
no automatic visual topology for unannotated PNG/PDF plans, no guarantee of
barrier-free travel or collision-free segment, no editing persistence, no
published graph. The frontend must review proposals and link to the future
Planning draft graph API after the team's `feature/planning-coordinate-system`
is integrated. No client may claim its candidate links are safe routes.
