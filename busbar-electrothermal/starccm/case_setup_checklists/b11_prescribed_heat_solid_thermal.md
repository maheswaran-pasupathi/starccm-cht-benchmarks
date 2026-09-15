# B11 setup checklist — straight bar, prescribed heat-source solid thermal benchmark

Purpose: isolate conduction + convection boundary-condition setup BEFORE
coupling real electrical Joule heating (B12). Checked against B02's
`variant_1_convection_only_constant_R()` steady lumped-temperature result.

## Geometry

Same straight bar as B10: 0.300 x 0.040 x 0.005 m copper, no bends.

## Physics — solid energy only, NO electrical model

1. `ThreeDimensionalModel`
2. `SteadyModel`
3. `SolidModel`
4. `SegregatedSolidEnergyModel` (enabled BEFORE `ConstantDensityModel` —
   order matters, see personal-mentor `hubbell_cae` Case 1 finding)
5. `ConstantDensityModel`

**Do NOT enable** any electrical model this case — B11 isolates thermal
setup only, matching the roadmap's stated purpose.

## Volumetric heat source

- Region-level physics condition:
  `EnergyUserVolumeSourceOption.Type.VOLUMETRIC_HEAT_SOURCE`, value set via
  `VolumetricHeatSourceProfile` on the REGION's `getValues()` (not a
  boundary condition — this is a body-force-like source over the whole
  solid volume).
- Value: **exactly B01's `q'''`** at 400A (67,200 W/m^3) — computed from
  the same `R = rho_e*L/A` used throughout this program, not re-derived
  independently, so B11's applied heat matches B02's `P_joule` input by
  construction.

## Boundary conditions

Convection (`WallThermalOption.Type.CONVECTION`, h=10 W/m^2K,
T_inf=293.15K) on **every** boundary, including both terminal end-caps —
unlike B10/B12, B11 has no electrical physics, so every face is available
for heat rejection (this matches B02's lumped-model assumption that the
ENTIRE external area rejects heat by convection).

## Reports

| Report | STAR-CCM+ construction |
|---|---|
| Applied heat check | `VolumeIntegralReport` of `UserSpecifiedEnergySource` field function over the region — must equal the prescribed total power to numerical precision (this is a construction check, not a physics check) |
| Heat rejected | `SurfaceIntegralReport` of `BoundaryHeatFlux` over all boundaries — should equal applied heat at steady state (a REAL physics check) |
| `Tmax`, `Tavg` | `MaxReport` / `VolumeAverageReport` of `Temperature` over the region |

**Field function names found live** (not obvious from class names — see
`docs/discrepancy_log.md`): the volumetric source field function is
`UserSpecifiedEnergySource`, NOT `VolumetricHeatSource`; the wall heat
flux field function is `BoundaryHeatFlux`, NOT `WallHeatFlux`.

## Acceptance gate (roadmap B11)

- Applied heat (volume integral) matches the prescribed total power to
  numerical precision (<0.1%, a construction check).
- Heat balance (rejected vs applied) within <1%.
- 3D average temperature is consistent with the 1D lumped abstraction —
  a DIFFERENCE from the 1D value is expected (real axial conduction
  gradient exists in 3D, absent from the lumped model) and must be
  EXPLAINED, not treated as an automatic failure.

## Result (2026-09-15)

**PASSED.** Applied-heat mismatch 2.0e-13% (construction check, exact).
Heat-balance mismatch 0.61% (<1% gate). 3D Tavg=34.63C vs B02's 1D lumped
34.93C — a 2.06%-of-rise difference, EXPLAINED by the visible axial
temperature gradient in `results/figures/b11_temperature.png` (peak at
the bar's center, cooler at both convecting end-caps) — exactly the
3D-vs-1D-abstraction difference the gate wording anticipates.
