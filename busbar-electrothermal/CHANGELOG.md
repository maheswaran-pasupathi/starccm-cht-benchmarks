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
