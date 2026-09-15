# Discrepancy log

Records every case where a result did not match its comparison target
(analytical, published reference, or a prior case), what the discrepancy
was, and how it was resolved or left open. Per the roadmap: never tune an
undocumented input silently -- every resolution here must be traceable to
a specific, stated cause.

No entries yet for THIS repository's own B00-B03 runs (all gates passed
on first implementation, see `docs/validation_matrix.md`).

## Carried-forward lesson from a related project (not this repo's own run)

**2026-09-15 -- personal-mentor `hubbell_cae` Case 6: area-normalized
contact resistivity reused across different contact areas.**

- **Discrepancy:** reusing the Siemens KB000126485 demo case's own
  bolted-joint contact-resistance value (2.6E-8 ohm.m^2) verbatim on a
  battery-pack-scale busbar terminal (128 mm^2, much smaller than the KB
  case's own terminal) produced a converged-but-nonphysical 474 K
  temperature rise.
- **Root cause:** `ElectricalResistanceAreaProfile` is area-normalized
  (ohm.m^2); actual resistance = value / contact_area. The KB value was
  calibrated for a different (larger) contact area and is not portable
  without rescaling.
- **Resolution:** derived the area-resistivity from a representative real
  bolted-copper-joint contact RESISTANCE (40 uOhm, ASSUMED generic) scaled
  to the actual contact area, giving a physically plausible 128 K rise.
- **Why it's carried into this repository:** `tests/test_units.py::
  test_area_normalized_contact_resistance_worked_example` reproduces this
  exact calculation as a regression test, so THIS program's B22 (bolted
  overlap joint) cannot repeat the same class of error silently.
- This is not a discrepancy in this repository's own results -- it is
  recorded here because the roadmap instructs preserving lessons that
  generalize, and this one directly bears on B22's design.

## 2026-09-15 -- B10: ConstantDensityModel dependency and current-balance sign convention

Two real issues found and resolved while getting B10 to run, both worth
recording so they are not rediscovered on B11/B12.

### 1. `ConstantDensityModel` requires an energy model registered first

- **Symptom:** `EnableModel` server error: "star.flow.ConstantDensityModel
  is incompatible with the currently enabled models ... no registration
  found."
- **Cause:** this API requires an energy model (e.g.
  `SegregatedSolidEnergyModel`) to be enabled before `ConstantDensityModel`
  can register -- consistent with the "model-enable order matters" pattern
  already found in the personal-mentor `hubbell_cae` project (Case 1: enable
  `SegregatedSolidEnergyModel` before `ConstantDensityModel`).
- **Resolution:** B10 is electrical-only by design (no energy model), and
  density is not used by anything active in this case (no
  buoyancy/transient-thermal-capacitance term exists without an energy
  model) -- so `ConstantDensityModel` was simply DROPPED, not
  worked around. Confirmed the case still solves correctly without it.
- **Not yet re-confirmed:** whether `ConstantDensityModel` is needed once
  B11/B12 add `SegregatedSolidEnergyModel` back -- expect to re-add it
  then, in the correct order.

### 2. Current-balance check must compare magnitudes, not signed values

- **Symptom:** first computed a spurious 200% "current imbalance" even
  though the underlying solve was later shown to match B01 to 1e-13%.
- **Cause:** `SurfaceIntegralReport` uses the boundary's OUTWARD normal by
  convention. At `Iout` (current entering the domain), the flux is
  measured AGAINST the outward normal, giving a negative signed value
  (-400.000...A); at `Vin` (current leaving to ground), the flux is WITH
  the outward normal, giving positive (+400.000...A). A perfectly balanced
  case therefore gives `I_iout = -I_vin`, not `I_iout = I_vin`.
- **Resolution:** changed the imbalance metric to compare `|I_iout|` vs
  `|I_vin|` (equivalently, check `|I_iout + I_vin| ~= 0`). After the fix,
  imbalance = 2.5e-12%, passing the <0.5% gate with enormous margin.
- **Carried forward:** any future boundary-flux balance check in this
  program (B11's heat balance, B12's boundary heat-flow-percentage report)
  must account for this same outward-normal sign convention before
  interpreting a raw magnitude mismatch as a real physics problem.

## 2026-09-15 -- B11: field function name guesses were wrong; 3D-vs-1D Tavg gap explained

### Field function names

- **Symptom:** `Server Error: Unable to generate report - Field function
  is not set` when trying to use `VolumetricHeatSource` and
  `WallHeatFlux`.
- **Cause:** neither name matches the actual registered field function
  name, despite matching the condition/profile CLASS names
  (`VolumetricHeatSourceProfile`, and the general "wall heat flux"
  concept). The real names, found via a diagnostic dump of every field
  function containing "heat"/"source"/"flux": `UserSpecifiedEnergySource`
  and `BoundaryHeatFlux`.
- **Resolution:** added an explicit diagnostic-dump-then-null-check
  pattern (throw a clear error naming the dump above, rather than let a
  null field function silently propagate into a report and fail
  opaquely later). Recorded the correct names in
  `starccm/field_functions.md` so this is not rediscovered for B12.

### 3D average temperature vs B02's 1D lumped prediction (2.06% of rise)

- **Comparison target:** B02 `variant_1_convection_only_constant_R()`,
  34.93C (`SIMULATED`, from the 1D lumped model).
- **Our result:** B11 3D `Tavg` = 34.63C (`SIMULATED`).
- **Discrepancy:** 0.30C absolute, 2.06% of the ~34.9K rise above ambient.
- **Investigation:** checked the temperature field
  (`results/figures/b11_temperature.png`) -- shows a clear, symmetric
  axial gradient: hottest at the bar's center, cooler toward both
  terminal end-caps (which ALSO convect in B11, unlike B10/B12).
- **Root cause:** the 1D lumped model in B02 assumes a single uniform bar
  temperature; the real 3D solution has an axial temperature profile
  because the end-caps are additional heat-rejection area not captured by
  a single-node lumped abstraction. This is NOT a bug -- it is exactly
  the kind of difference the roadmap's own B11 gate wording anticipates
  ("differences in Tmax explained by axial/three-dimensional conduction
  rather than treated as failure").
- **Resolution:** no code change; the difference is explained and
  accepted. The heat-balance gate (<1%) and applied-heat gate (<0.1%)
  both passed independently, confirming the 3D setup itself is correct --
  the Tavg gap is a genuine modeling-abstraction difference, not an error.
- **Re-verification:** not applicable (no fix was needed).

## 2026-09-15 -- B12: "Maximum Steps" is a cumulative, whole-simulation counter

- **Symptom:** running two variants (two separate regions) in ONE
  simulation via two sequential `sim.getSimulationIterator().run(3000)`
  calls gave variant 1 a normal, well-converged solve, but variant 2
  solved for exactly 1 iteration and produced a nonsensical result
  (I_iout=0A, I_vin=0A, imbalance=NaN%, huge power/heat mismatches).
- **Cause:** `Maximum Steps` is a whole-simulation, CUMULATIVE iteration
  count, not reset or scoped per `run()` call or per region. After
  variant 1 finished at iteration 3000, the stopping criterion was
  already satisfied when variant 2's identical `run(3000)` call was
  issued, so the solver did almost nothing.
- **Resolution:** call `run()` with an INCREASING absolute target each
  time a new variant/region is added to the same simulation (3000, then
  6000, ...), not the same relative step count. Confirmed working:
  variant 2 then genuinely solved from iteration 3000 to 6000.
- **Carried forward:** any future case that runs multiple variants inside
  one simulation (rather than one `-new` simulation per variant) must use
  this pattern. Running each variant as a fully separate simulation
  (`-new` per macro invocation) avoids the issue entirely and may be
  preferable for future cases where iteration counts are less predictable.

## Template for future entries

```
### YYYY-MM-DD -- <case ID>: <one-line summary>

- **Comparison target:** (analytical / published reference / prior case), value, status label
- **Our result:** value, status label
- **Discrepancy:** magnitude and direction
- **Investigation:** what was checked (units, BC, mesh, property source, geometry)
- **Root cause:** stated explicitly, or "not yet identified" if still open
- **Resolution:** what was changed, OR "left open, tracked as risk in docs/limitations.md"
- **Re-verification:** result after fix, confirming the gate now passes
```
