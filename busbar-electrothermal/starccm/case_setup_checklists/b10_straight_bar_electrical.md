# B10 setup checklist — straight bar, 3D solid electrical benchmark

Purpose: verify STAR-CCM+'s electrical potential/current solution in
isolation, with NO thermal coupling, checked directly against `src/b01_straight_busbar.py`'s
closed-form result for the identical geometry/material/current.

## Geometry

- One solid rectangular bar, straight (no bends — this is B10, not B21).
- Dimensions **must match B01's baseline exactly** for the comparison to be
  meaningful: length 0.300 m, width 0.040 m, thickness 0.005 m (see
  `data/source_traceability.csv` for provenance — these are ASSUMED
  generic values, not a cited product).
- Two terminal end-cap faces (perpendicular to the bar's long axis), named
  `Vin` (x=0 end) and `Iout` (x=L end) — reusing the naming convention from
  the personal-mentor `hubbell_cae` project's Cases 3-7 for consistency.
- Refine the mesh near BOTH terminal faces (local surface size control),
  so entrance-effect current crowding near the terminals can be visually
  and numerically distinguished from the fully-developed bulk region —
  this is why the gate checks section-average J "away from terminals",
  not at the terminal faces themselves.

## Physics — Solid continuum, NO energy/thermal models this case

Enable, in this order (order matters — enabling `ElectrodynamicsPotentialModel`
before its parent `ElectromagnetismModel` throws "no registration found";
found live in personal-mentor Case 3, RUN_LOG.md 2026-09-15):

1. `ThreeDimensionalModel`
2. `SteadyModel`
3. `SolidModel`
4. `ElectromagnetismModel` (parent — must be explicit, GUI auto-attach does
   NOT happen via the raw macro `enable()` API)
5. `ElectrodynamicsPotentialModel`

**Do NOT enable** `SegregatedSolidEnergyModel`, `OhmicHeatingModel`, or
`ConstantDensityModel` this case. The first two are B11/B12 scope
(electrical-only per the roadmap). `ConstantDensityModel` was tried and
found to require an energy model registered first (`EnableModel` server
error) — found live, see `docs/discrepancy_log.md` 2026-09-15. Density is
not used by anything active without an energy model, so it is simply
omitted, not worked around.

## Material — copper, CONSTANT conductivity this run

- Electrical conductivity: `sigma = 1 / rho_e_ref` using the **same**
  `rho_e_ref = 1.68e-8 ohm.m` from `data/materials.csv` that B01 uses —
  not the 1.72e-8 value used in the earlier, separate personal-mentor
  `hubbell_cae` project. Using a different reference value would make the
  B10-vs-B01 comparison meaningless.
- Density/specific heat: irrelevant this case (no energy model active),
  but STAR-CCM+'s `SolidModel` may still require the property slots to be
  populated — set them anyway from `materials.csv` to avoid a
  placeholder-material trap (see the parallel personal-mentor
  `starccm-cht-benchmarks` Case 1 finding: an unset material property
  silently used STAR-CCM+'s own placeholder, producing a near-isothermal,
  physically-wrong result that looked like a converged answer).

## Boundary conditions

- `Iout`: `ElectrodynamicsPotentialWallOption.ELECTRIC_CURRENT` = 400 A
  (matches B01's `BASELINE_CURRENT_A`).
- `Vin`: `ElectrodynamicsPotentialWallOption.ELECTRIC_POTENTIAL` = 0 V
  (ground/reference).
- All other faces: default `INSULATOR` (zero normal current) — do not
  override.

## Reports / monitors (roadmap-required list)

| Report | STAR-CCM+ construction |
|---|---|
| Current through `Iout` | `SurfaceIntegralReport` of the boundary-normal current-density field function over `Iout` (should equal the prescribed 400 A by construction — checks BC application, not solver accuracy) |
| Current through `Vin` | Same construction over `Vin` — this is the MEANINGFUL current-balance check (a solved, not prescribed, quantity) |
| Average terminal voltage (`Iout`, `Vin`) | `AreaAverageReport` of `ElectricPotential` over each boundary |
| Voltage drop | `V(Iout) - V(Vin)` |
| Volume integral of `J·E` | Custom `UserFieldFunction` computing the dot product of `ElectricCurrentDensity` and `ElectricField` vector field functions, then `VolumeIntegralReport` over the region |
| Max current density | `MaxReport` of `ElectricCurrentDensity`'s magnitude function over the region |
| Section-average current density (away from terminals) | `AreaAverageReport` of the same magnitude function over a `PlaneSection` derived part cut at the bar's midpoint (x = L/2), far from both terminal entrance-effect zones |

## Acceptance gate (roadmap Section 3 / B10)

- `|I_Iout - I_Vin| / I_Iout < 0.5%` (current balance)
- `|V_drop_STARCCM - V_drop_B01| / V_drop_B01 < 1%` AND same for total power
- `|J_section_avg - I/A| / (I/A) < 1%`, evaluated away from the terminals

## Required images (roadmap Section 7)

1. Geometry with named boundaries (`Vin`, `Iout`, sides).
2. Mesh overview + terminal-region close-up (showing the refinement).
3. Electric potential field.
4. Current-density magnitude field (+ the mid-bar section-average sample
   plane, so the "away from terminals" claim is visually verifiable).
5. Convergence/monitor history (ElectricPotential residual).

## Reconstruction instructions

Run: `starccm+ -new -batch starccm/macros/b10_straight_bar_electrical.java`
No GUI interaction required — fully scripted, matching this project's
established macro-first workflow. Requires a valid STAR-CCM+ license with
the base `ccmpsuite` feature (Electromagnetics/Electrodynamics did NOT
require a separate license feature on the machine this was developed on —
confirmed live in the personal-mentor `hubbell_cae` investigation,
RUN_LOG.md 2026-09-15 — but this has not been independently re-confirmed
on a different license/machine for this repository).
