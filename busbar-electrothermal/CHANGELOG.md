# Changelog

## 2026-09-15 -- First increment: B00-B03 analytical/1D benchmarks

- Repository scaffold created under `busbar-electrothermal/`.
- `data/materials.csv` + `data/source_traceability.csv`: copper/aluminium
  properties with full source citation (Incropera & Bergman, CRC Handbook,
  CODATA), every `ASSUMED` value flagged with rationale.
- `src/materials.py` (B00): temperature-dependent resistivity/conductivity,
  unit-conversion helpers. 18 tests, all passing.
- `src/b01_straight_busbar.py` (B01): closed-form electrical benchmark,
  current/thickness/width/material/temperature sweeps. 7 tests, all
  passing. CSV + 3 plots generated.
- `src/b02_steady_thermal.py` (B02): nonlinear steady energy-balance root
  solver (convection, radiation, temperature-dependent R, end conduction),
  all 5 required variants plus an h/emissivity/T_inf/T_end sensitivity
  sweep. 8 tests, all passing, normalized residual < 1.4e-11 (gate: <1e-6).
  CSV + 2 plots generated.
- `src/b03_transient_trn.py` (B03): one-node transient ODE (numeric via
  `scipy.integrate.solve_ivp`) verified against the analytical step
  response to 6.9e-8 K (gate: <1e-3 K); four load-profile generators
  (step, overload pulse, duty cycle, cooling-after-off); three-node network
  scaffold. 8 tests, all passing. CSV + 1 plot generated.
- `docs/units_and_sign_conventions.md`, `docs/validation_matrix.md`,
  `docs/discrepancy_log.md`, `docs/assumptions.md`, `docs/limitations.md`
  created.
- **41/41 tests passing.** B10 (3D STAR-CCM+ electrical benchmark) not yet
  started -- see `docs/validation_matrix.md`.
- Open item, not silently resolved: repository license not yet chosen.

## 2026-09-15 -- Second increment: B10 (3D STAR-CCM+ electrical benchmark)

- `starccm/case_setup_checklists/b10_straight_bar_electrical.md`,
  `starccm/field_functions.md`, `starccm/export_schema.md`: setup
  checklist, field-function/derived-part definitions, and export schema
  written BEFORE the macro, per the roadmap's ordering rule.
- `starccm/macros/b10_straight_bar_electrical.java`: straight 0.300 x
  0.040 x 0.005 m copper bar, 400 A prescribed current, electrical-only
  (`SolidModel` + `ElectromagnetismModel` + `ElectrodynamicsPotentialModel`,
  deliberately no energy/Ohmic-heating model). Same material constants as
  B01 (`rho_e_ref = 1.68e-8 ohm.m`) for a meaningful direct comparison.
- **Gate PASSED, all three checks, at essentially machine precision:**
  current balance 2.5e-12% (gate <0.5%), voltage-drop-vs-B01 5.2e-14% and
  power-vs-B01 2.0e-13% (gate <1%), section-average-J-vs-nominal 2.3e-14%
  (gate <1%). Expected result for a uniform straight bar with no geometric
  crowding -- confirms STAR-CCM+'s electrical solver reproduces the 1D
  closed form exactly for this case.
- `tests/test_b10_vs_b01.py`: independent Python-side verification against
  B01's own live-computed values (not a hardcoded copy) -- 4 tests, skips
  cleanly (not a failure) if B10 has not been run.
- Two real issues found and documented in `docs/discrepancy_log.md`:
  (1) `ConstantDensityModel` requires an energy model registered first,
  and was simply dropped (unused without an energy model) rather than
  worked around; (2) the current-balance check must compare boundary-flux
  MAGNITUDES, not signed values, because `SurfaceIntegralReport` uses the
  outward-normal convention (current entering vs leaving a boundary have
  opposite sign by construction, not by error).
- `results/figures/b10_{geometry,potential,current_density}.png` show the
  expected textbook-linear potential gradient and uniform current density.
- **45/45 tests passing** (41 analytical + 4 new B10 checks).

## 2026-09-15 -- Third increment: B11 (prescribed-heat solid thermal)

- `starccm/macros/b11_prescribed_heat_solid_thermal.java`: same straight
  bar as B10, prescribed uniform volumetric heat source (region-level
  `EnergyUserVolumeSourceOption.VOLUMETRIC_HEAT_SOURCE`) set to EXACTLY
  B01's `q'''` (67,200 W/m^3 at 400A), convection on every face
  (h=10 W/m^2K, T_inf=293.15K, matching B02's variant 1).
- **Gate PASSED:** applied-heat-vs-prescribed 2.0e-13% (exact); heat
  balance (rejected vs applied) 0.61% (<1% gate).
- **3D Tavg (34.63C) vs B02's 1D lumped prediction (34.93C): 2.06% of
  rise, EXPLAINED, not a failure** -- the real 3D solution has an axial
  temperature gradient (hot center, cooler convecting end-caps) that a
  single-node lumped model cannot capture, visible in
  `results/figures/b11_temperature.png`.
- Two field-function name guesses were wrong
  (`VolumetricHeatSource`/`WallHeatFlux`); the real names
  (`UserSpecifiedEnergySource`/`BoundaryHeatFlux`) were found via a
  diagnostic dump and recorded in `starccm/field_functions.md`.
