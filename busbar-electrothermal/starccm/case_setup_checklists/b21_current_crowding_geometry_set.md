# B21 setup checklist — current-crowding geometry set

Per the roadmap's B21 section: a parameterized geometry family with equal
terminal current (400 A, matching B10-B12/this repo's established
baseline) and a comparable overall envelope (0.040 x 0.005 m
cross-section throughout; ~0.300 m developed length throughout), so that
Jmax/Javg, percentile current density, resistance, and Tmax differences
across variants are attributable to GEOMETRY, not to a changed current or
cross-section.

## Variants (day-3 scope: A-E; F deferred, see below)

| ID | Description | Geometry construction technique |
|---|---|---|
| B21-A | Straight reference | **Reused directly from B12 v1** (identical geometry, current, and physics chain — see "B21-A reuse" below) |
| B21-B | Central circular hole | `SimpleCylinderPart` + `SubtractPartsOperation` (proven technique, personal-mentor `hubbell_cae` Case 6 bolt holes) |
| B21-C | Narrowed neck | Two symmetric `SimpleBlockPart` notches + `SubtractPartsOperation` |
| B21-D | Sharp 90° inner corner | 3D-CAD `Sketch` + `SweepMerge` along a sharp-cornered path (proven technique, personal-mentor `hubbell_cae` Case 3) |
| B21-E | Filleted 90° bend | Same as B21-D, with a fillet applied to the inner-corner path sketch — **best-effort; see blocker note below** |
| B21-F | Two-hole/bolt-pattern proxy | **Deferred** — the roadmap's day-3 exit gate only requires A-E; F is listed in the full B21 spec as a later addition. Not silently dropped: recorded as `PENDING` in the validation matrix, not skipped without note. |

## B21-A reuse (not a new run)

B21-A's own spec (straight reference, 400A, same cross-section) is
*identical* to B12's own "v1_constant_properties" case already run and
gated PASSED in this repository: same 0.300x0.040x0.005m copper bar,
same 400A current-driven electrical BC, same convection-on-sides/
adiabatic-terminals thermal BC, same constant material properties. Rather
than re-run an identical case under a new name, B21-A's row in
`results/processed/b21_results.csv` is populated directly from
`results/processed/b12_results.csv`'s `v1_constant_properties` row. This
is documented here, not silently duplicated as if it were new work.

## Common physics chain (B21-B through B21-D/E) — same as B12 v1

1. `ThreeDimensionalModel`, `SteadyModel`, `SolidModel`
2. `SegregatedSolidEnergyModel` (before `ConstantDensityModel`)
3. `ConstantDensityModel`
4. `ElectromagnetismModel` (explicit parent)
5. `ElectrodynamicsPotentialModel`
6. `OhmicHeatingModel`

Constant properties only (temperature-dependent resistivity, B12's own
v2 extension, is out of scope for B21 — current-crowding geometry effects
are the variable under study here, not the T-dependence coupling already
quantified in B12).

## Boundary conditions — same convention as B12

- One terminal: `ELECTRIC_CURRENT` = 400 A.
- Other terminal: `ELECTRIC_POTENTIAL` = 0 V (ground).
- All non-terminal outer faces (including the new hole/neck/corner
  surfaces): convection, `h=10 W/m^2K`, `T_inf=293.15K` (same as B02/B11/
  B12 — the "generic natural convection" `ASSUMED` value, not B20's
  one-off 293.0K).
- Terminal end-caps: adiabatic (same as B12).

## Metrics per the roadmap's B21 spec

- `Jmax` (STAR-CCM+ `MaxReport`, whole region) — reported but NOT used
  alone as the crowding metric, per the roadmap's own rule ("never use a
  single-node or mathematically singular maximum as the only metric").
- `J95`/`J99` (95th/99th percentile of the current-density-magnitude
  field, EXCLUDING cells within a stated exclusion distance of the two
  terminal faces where the electrical BC itself creates an idealized,
  non-physical entrance-effect singularity) — computed in Python from a
  per-cell field export (`XyzInternalTable`), not from a hardcoded
  STAR-CCM+ percentile report (this STAR-CCM+ version's report catalog
  was not found to include a direct percentile/histogram report type
  during this case's setup — recorded as a real, checked-for-and-absent
  capability, not an unexamined assumption).
- Total resistance (`V_drop / I`) and voltage drop.
- Integrated loss in a defined "hotspot zone" (cells with `J > 1.5x` the
  straight-bar nominal `J`, i.e. `> 3.0e6 A/m^2`) vs total loss.
- `Tmax`, hotspot location (position of the `Tmax` cell), thermal
  spreading (`Tmax - Tavg`).
- Exclusion distance for J95/J99 and hotspot-zone definitions are stated
  explicitly in `src/b21_postprocess.py`, not silently baked into the
  STAR-CCM+ macro where they would be harder to audit.

## Known blocker check: B21-E fillet

STAR-CCM+ 3D-CAD `Sketch` fillet capability was checked live before
attempting B21-E. If a working fillet method is not found on this
version's `Sketch`/`SketchPrimitive` API within a reasonable, documented
attempt, B21-E is marked `PENDING`/blocked in the validation matrix with
the specific error recorded in `docs/discrepancy_log.md` — NOT
approximated with an un-filleted sharp corner mislabeled as "filleted."
