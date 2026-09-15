# Changelog

## 2026-09-15 -- Sixth increment: B21 current-crowding geometry set (A-E)

- `starccm/case_setup_checklists/b21_current_crowding_geometry_set.md`,
  `starccm/macros/b21{b,c,d,e}_*.java`: 4 new 3D STAR-CCM+ cases (B21-A
  reused directly from B12's already-passed v1_constant_properties case,
  not re-run), all at 400A / 0.040x0.005m cross-section for direct
  comparability:
  - **B21-B (central hole):** SimpleCylinderPart + SubtractPartsOperation.
  - **B21-C (narrowed neck):** two symmetric SimpleBlockPart notches +
    SubtractPartsOperation.
  - **B21-D (sharp 90-degree corner):** 3D-CAD Sketch + SweepMerge (reused
    technique from personal-mentor `hubbell_cae` Case 3).
  - **B21-E (filleted 90-degree corner):** same as D plus
    `Sketch.createSketchFillet` -- found via `javap` against
    `cadmodeler.jar` after a first guessed method name
    (`createFilletBetweenLines`) failed to compile.
- **All 5 variants PASSED** their current-balance/power/heat-balance
  gates. B21-C needed a real fix: FAILED heat balance at 5.03% at the
  standard 3000 iterations (Energy residual 3-4x higher than the other
  variants at the same count -- the neck's abrupt cross-section step is
  stiffer to converge), PASSED at 0.61% after raising to 5500 iterations.
  The original failed attempt's log/CSV are preserved
  (`*_attempt1_3000iter_FAILED`), not overwritten.
- `src/b21_postprocess.py`: computes `J95`/`J99` (95th/99th percentile
  current density, excluding a stated exclusion zone near each terminal)
  and a hotspot-cell fraction from each macro's per-cell `XyzInternalTable`
  field export -- no direct percentile report was found in this STAR-CCM+
  version's report catalog, checked and documented rather than assumed
  absent. `results/processed/b21_crowding_summary.csv`.
- **Result: B21-C (neck) is the worst current-crowding case by every
  metric** (J95/Jnominal=2.00x, J99/Jnominal=2.09x, 11.7% of cells in the
  hotspot zone, Tmax=38.56C -- all the highest of the 5 variants).
- **Counter-intuitive, honestly-reported finding:** B21-E's fillet cuts
  the sharp corner's singular `Jmax` by 44% but RAISES both `J95`/`J99`
  and `Tmax` slightly vs the unfilleted B21-D -- exactly the scenario the
  roadmap's B21 section warns about ("never use a singular maximum as the
  only metric"). Flagged as unconfirmed against mesh refinement, not
  forced to match textbook intuition (`docs/discrepancy_log.md`).
- `data/source_traceability.csv`, `docs/limitations.md` updated with the
  new geometry `ASSUMED` values and the exclusion-distance/hotspot-
  threshold/no-mesh-independence-check limitations.

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

## 2026-09-15 -- Fifth increment: B20 source audit and simplified proxy

- **Audit finding, reported before any B20 code was written:** the
  roadmap's Section 9 claim that COMSOL's public busbar example is
  "commonly described with a 160 A DC load and a published maximum
  temperature near 330.42 K" could not be verified. Fetched and fully
  read BOTH official COMSOL busbar model-report PDFs (`busbar_llac`, the
  base tutorial, and `busbar_llinventor`, the busbar-assembly tutorial) --
  neither states 160 A or 330.42 K anywhere. `busbar_llac` is
  voltage-driven (20 mV, not current) and reports 42-52C; `busbar_llinventor`
  is current-density-driven (8,000 A/m^2, not a lumped current) and
  reports 60-100C. `330.42K = 57.27C` falls outside both ranges. Full
  write-up: `docs/b20_comsol_audit.md`.
- `data/source_traceability.csv`: the roadmap's 160A/330.42K entries
  downgraded from `REFERENCE` to `UNVERIFIED` (kept, not deleted); four
  new rows added for the VERIFIED `busbar_llac` values actually used
  (20mV BC, 293K ambient, copper+titanium materials, <10K reported
  deltaT).
- `docs/discrepancy_log.md`, `docs/validation_matrix.md`: B20 entry
  updated to record this as a resolved source-provenance finding, not a
  solver bug.
- `starccm/case_setup_checklists/b20_voltage_driven_proxy.md`,
  `starccm/macros/b20_voltage_driven_proxy.java`: B20 implemented as the
  roadmap's own explicitly-sanctioned "simplified STAR-CCM+ proxy" --
  reuses B12's straight-bar geometry/physics chain, but with BOTH
  terminals `ELECTRIC_POTENTIAL` (20mV / 0V, verified BC methodology from
  `busbar_llac`) instead of B10/B12's prescribed-current BC. Explicitly
  does NOT claim to reproduce the real tutorial's geometry, materials, or
  `Tmax` -- only validation-hierarchy items 1-3 (BC/methodology audit,
  electrical power/voltage checks, boundary heat-flow audit) are in scope.
- Results: see `results/processed/b20_results.csv` and the run log for
  gate status.

## 2026-09-15 -- Fourth increment: B12 (coupled DC electro-thermal) -- B10-B12 complete

- `starccm/macros/b12_coupled_electrothermal.java`: combines B10's
  electrical setup with B11's thermal setup, adding `OhmicHeatingModel`
  so `q''' = J.E` is a real, SOLVED source term. Two variants in one
  macro: constant properties, and temperature-dependent resistivity
  (clamped linear model, same fix as personal-mentor `hubbell_cae` Case 5).
- **Both variants PASSED all three gates** (current balance, power vs
  `I*V`, heat balance). v1 (constant): current 2.6e-12%, power 8.8e-14%,
  heat 0.67%. v2 (T-dependent): current 1.8e-6%, power 4.9e-6%, heat 0.88%.
- **Quantified the positive-feedback coupling** the roadmap explicitly
  asks for ("do not only state it"): v2's voltage drop (0.01075V) and
  Tmax (35.78C) are both ~6.6%/higher than v1's constant-property result
  (0.01008V, 34.84C) at the identical 400A -- a real, measured effect of
  resistivity rising with temperature.
- Real issue found and documented: `Maximum Steps` is a CUMULATIVE,
  whole-simulation iteration counter, not reset per `run()` call --
  running variant 2 with the same relative step count as variant 1 did
  almost nothing (already-satisfied stopping criterion). Fixed by using
  an increasing absolute target per variant (3000, then 6000).
- **B10-B12 (the roadmap's originally requested scope for this session)
  are now all PASSED.** `docs/validation_matrix.md` is the authoritative
  status table; B20 onward remain `PENDING`.
