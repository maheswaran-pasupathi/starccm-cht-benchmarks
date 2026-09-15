# B12 setup checklist — straight bar, coupled DC electro-thermal

Purpose: combine B10's electrical setup with B11's thermal setup, adding
`OhmicHeatingModel` so `q''' = J.E` is a REAL, solved source term (not
prescribed as in B11). Two variants per the roadmap's progression items
1-2 (items 3-5 — temperature-dependent k, radiation, transient —
explicitly deferred, not silently skipped).

## Physics — all three physics families together

1. `ThreeDimensionalModel`, `SteadyModel`, `SolidModel`
2. `SegregatedSolidEnergyModel` (before `ConstantDensityModel`)
3. `ConstantDensityModel`
4. `ElectromagnetismModel` (explicit parent)
5. `ElectrodynamicsPotentialModel`
6. `OhmicHeatingModel` — the coupling term

## Boundary conditions

- `Iout`/`Vin`: same electrical BCs as B10 (400A current / 0V ground).
- Four side faces: convection, same as B11 (h=10 W/m²K, T∞=293.15K).
- Terminal end-caps: **adiabatic by default** (not convecting) — they
  carry the electrical BC instead. Confirmed via `Q_terminals` report,
  which reads exactly 0.0 W in both variants.

## Two variants, in ONE macro invocation

- **v1: constant properties** — `sigma` fixed at the 293.15K reference
  value, matching B01/B10 exactly for direct comparison.
- **v2: temperature-dependent resistivity** — linear `sigma(T)` (same
  model as `src/materials.py`), clamped beyond 420K (same fix as
  personal-mentor `hubbell_cae` Case 5 — a naive unclamped linear fit
  crosses zero conductivity and blows up the solve).

## Real issue found and fixed: cumulative iteration counter

Running two variants (two separate regions) in ONE simulation via two
sequential `run(3000)` calls does **not** give variant 2 its own 3000
iterations. `Maximum Steps` is a whole-simulation, cumulative iteration
counter — after variant 1 reaches iteration 3000, calling `run(3000)`
again for variant 2 immediately satisfies the stopping criterion (already
at iteration 3000) and the solve does almost nothing (observed: 1
iteration, wildly wrong result: 0A current, NaN imbalance). **Fix:** call
`run()` with an INCREASING absolute target each time (3000, then 6000, ...).
See `docs/discrepancy_log.md` for the full write-up.

## Result (2026-09-15)

**Both variants PASSED.**

| | v1 (constant) | v2 (T-dependent) |
|---|---|---|
| Current imbalance | 2.6e-12% | 1.8e-6% |
| Power mismatch (`J.E` vs `I*V`) | 8.8e-14% | 4.9e-6% |
| Heat balance | 0.67% | 0.88% |
| Voltage drop | 0.01008 V | **0.01075 V (+6.6%)** |
| Tmax | 34.84 C | **35.78 C** |

**The quantified feedback** (roadmap: "quantify this feedback; do not
only state it"): v2's higher operating temperature raises copper's
resistivity, raising the voltage drop and total Joule loss by ~6.6% at
the SAME 400A prescribed current, compared to the constant-property
assumption — a real, measured coupling effect, not an assertion.

Variant 2 took substantially longer to converge (~6000 iterations vs
v1's ~3000, ElectricPotential residual settling around 1e-6 rather than
1e-13) — expected for a genuinely nonlinear coupled solve; the achieved
residual level is still tight enough that the gate checks (current,
power, heat balance) all pass with real margin.
