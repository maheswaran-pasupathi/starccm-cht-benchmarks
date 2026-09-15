# B22 setup checklist — bolted overlap joint with prescribed contact resistance

Per the roadmap's B22 section: "Use a generic two-bar overlap joint" with
a model ladder (perfect contact; uniform electrical `Rc,e`; independent
thermal `Rc,th`; ...) and a minimum sweep of "perfect contact; low,
medium, and high interface resistance spanning a documented public
range." This increment implements the day-3 core scope: **perfect
contact plus low/nominal/high electrical contact resistance.** Thermal
contact resistance is explicitly **deferred** (see below) rather than
invented.

## Geometry — 4 blocks, not 2 (real finding, see `docs/discrepancy_log.md`)

A first attempt built the overlap joint as just two blocks (bar A, bar B)
sharing a coincident face and called `createDirectInterface()` on their
full top/bottom faces. This failed two ways in sequence, both diagnosed
live before writing the real macro:

1. Meshing both blocks in ONE `AutoMeshOperation` gave `Surface intersects
   self` — coincident-face parts must be meshed in SEPARATE operations.
2. Even after fixing that, `createDirectInterface(fullTopOfA, fullBottomOfB)`
   gave an interface with `NaN` area, because bar A's full top face
   (its whole length) and bar B's full bottom face (its whole,
   DIFFERENT-position length) only PARTIALLY coincide — `createDirectInterface`
   needs exactly-matching faces, not a partial-overlap pair.

**Fix:** each bar is split into two separate blocks at the overlap
boundary — `barA_free` + `barA_overlap`, `barB_overlap` + `barB_free` —
so the two "overlap" blocks have an EXACTLY matching `W x OVERLAP`
footprint. Three interfaces are created:

- `A_internal`: `barA_free` ↔ `barA_overlap` — a PERFECT (zero-resistance)
  interface, purely for mesh/region continuity within bar A itself.
- `B_internal`: `barB_overlap` ↔ `barB_free` — same, for bar B.
- `JointContact`: `barA_overlap` ↔ `barB_overlap` — **this is the real
  bolted-joint contact**, where the electrical (and, if added later,
  thermal) contact resistance is actually applied.

Confirmed live (diagnostic macro, `_diag_b22_overlap_geometry.java`,
not committed — throwaway): `JointContact` contact area = 8.0e-4 m²
(exactly `W x OVERLAP` as designed), `A_internal`/`B_internal` areas =
2.0e-4 m² (exactly `W x T`). Interface boundary areas read as `NaN`
until at least one solver iteration ran — the interface's geometric
projection/intersection mapping is resolved lazily, not at creation
time. The real macro runs a normal solve, so this is not an issue there,
but is recorded since it could confuse a future live-diagnostic attempt.

## Interface-level API (found live via `javap` + a diagnostic dump)

No documentation was consulted for this — the actual condition/value
classes exposed on a solid-solid contact `BoundaryInterface` were found
by creating a real interface with the full electro-thermal physics
chain enabled and dumping `interface.getConditions().getObjects()`,
`interface.getValues().getObjects()`:

- **Electrical:** `star.electromagnetism.common.ElectricalResistanceOption`
  (interface-level condition), `Type.PERFECT_CONDUCTOR` (default) vs
  `Type.SPECIFIC_ELECTRICAL_RESISTANCE` (area-normalized, paired with
  `ElectricalResistanceAreaProfile`, Ω·m²) vs `Type.TOTAL_ELECTRICAL_RESISTANCE`
  (absolute Ω, not used here — this repo's convention is area-normalized,
  matching `docs/units_and_sign_conventions.md` and the existing
  `test_area_normalized_contact_resistance_worked_example` regression test).
- **Thermal:** `star.energy.ContactInterfaceThermalOption` (interface-level
  condition, `Type.CONJUGATE_HEAT_TRANSFER` default) with
  `star.energy.ThermalContactResistanceProfile` (K·m²/W) as an interface
  Value — present but NOT swept this increment (see below).
- Also present but unused: `ElectricCurrentContactSurfaceSourceOption`,
  `EnergyUserSurfaceSourceOption` (interface-level surface source
  bookkeeping, handled internally by the solver once the resistance
  options above are set — not manipulated directly).

## Model ladder implemented this increment

1. **Perfect contact** — `ElectricalResistanceOption.Type.PERFECT_CONDUCTOR`
   (STAR-CCM+ default; explicit for clarity).
2. **Low** — `SPECIFIC_ELECTRICAL_RESISTANCE`, 2.4e-9 Ω·m² (from 3 µΩ,
   Storm Power Components, silver-plated bolted busbar joint — see
   `data/source_traceability.csv`).
3. **Nominal** — 1.6e-8 Ω·m² (from 20 µΩ, PEM, contact-enhanced bolted
   joint).
4. **High** — 8.4e-8 Ω·m² (from 105 µΩ, PEM, traditional/untreated
   bolted copper joint).

All four cases: same 400A current, same cross-section
(0.040×0.005m), same physics chain as B12/B21 (`SolidModel` +
`SegregatedSolidEnergyModel` + `ConstantDensityModel` +
`ElectromagnetismModel` + `ElectrodynamicsPotentialModel` +
`OhmicHeatingModel`), convection on all outer faces except the two
current-carrying terminal end-caps.

## Deferred, not fabricated: thermal contact resistance

The roadmap's B22 minimum sweep also asks for thermal contact resistance
"varied independently to avoid conflating heat generation with heat
removal" — but this is explicitly listed as item 3 of the model LADDER
(a later, not first, rung) and the day-3 exit gate only requires the
electrical ladder as core, with thermal "if time permits." A search for
a citable ROOM-TEMPERATURE (290–350K) bolted-copper-busbar thermal
contact resistance value did not find one; the one detailed primary
source located (Schmitt et al., SuperCDMS bolted copper joints,
FERMILAB-PUB-14-522-PPD) is a CRYOGENIC study (60 mK–26 K,
phonon-transport-dominated) and is explicitly NOT used, since applying
a cryogenic value to a room-temperature busbar would be scientifically
wrong, not merely imprecise. `ThermalContactResistanceProfile` is left
at its default (perfect, i.e. no added thermal resistance) in all four
cases this increment. This is recorded as `PENDING`/`MISSING` in
`data/source_traceability.csv`, not silently treated as validated.

## Reports (per the roadmap's B22 "Reports" list)

- Contact voltage drop: `V(barA_overlap side) - V(barB_overlap side)`
  at the `JointContact` interface, area-averaged each side.
- Total voltage drop: full-assembly terminal-to-terminal.
- Bulk loss: volume integral of `J·E` summed over all four solid
  regions (the contact resistance's own Joule heating is injected as an
  interface SURFACE source, not into this volume integral — confirmed
  via the interface-level `EnergyUserSurfaceSourceOption`/
  `ElectricCurrentContactSurfaceSourceOption` conditions found above).
- Interface loss: `I_measured^2 * R_contact_prescribed` (analytical,
  since the contact resistance is a known prescribed input) — reported
  alongside `total_loss - bulk_loss` as an independent cross-check that
  the two agree, rather than trusting either number alone.
- Fraction of total loss generated at the interface.
- Temperature jump across the contact: area-averaged `T` on each side of
  `JointContact`.
- Current-density pattern before/through/after the joint: reported via
  `Jmax` in each of the four regions separately (free-A, overlap-A,
  overlap-B, free-B) rather than one whole-assembly maximum, so the
  current redistribution around the joint is visible per the roadmap's
  own "current distribution before, through, and after the joint" ask.

## Critical reasoning (per the roadmap's B22 section, stated not silently assumed)

- This is a purely thermal/electrical contact-resistance model. It does
  **not** claim to predict bolt preload, real contact area, or
  constriction resistance from first principles — the prescribed
  resistance values are measured/published inputs, not derived from a
  structural/contact-mechanics model.
- Surface condition, plating, and aging are real uncertainty drivers
  (the >30x spread between the low and high cited values, 3 µΩ to
  105 µΩ, is itself evidence of this) — the low/nominal/high sweep is
  reported as a sensitivity envelope, not as three equally-likely
  point estimates.
