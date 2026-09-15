# Units and sign conventions

Status: B00 deliverable. Applies to every script, test, and STAR-CCM+ case
in this repository.

## Base units (SI, no exceptions)

| Quantity | Unit | Symbol |
|---|---|---|
| Length | metre | m |
| Area | square metre | m^2 |
| Electrical resistivity | ohm.metre | ohm.m |
| Electrical conductivity | siemens/metre | S/m |
| Current | ampere | A |
| Voltage | volt | V |
| Resistance | ohm | ohm |
| Power / heat rate | watt | W |
| Temperature | kelvin | K (Celsius only in plot axis labels, never in calculations) |
| Heat capacity (specific) | J/(kg.K) | cp |
| Thermal capacitance | J/K | Cth |
| Thermal resistance | K/W | Rth |
| Contact resistance (area-normalized) | ohm.m^2 | Rc,e (electrical), Rc,th [K.m^2/W] (thermal) |
| Time | second | s |

Any function or CSV column not in these units must say so explicitly in
its name (e.g. `WC_mm`, `area_mm2`) and be converted at the boundary via
`src/materials.py`'s conversion helpers -- never inline `* 1e-6` scattered
through the codebase.

## Common unit traps this project has already hit once (see personal-mentor
`hubbell_cae/sample_portfolio/RUN_LOG.md`, 2026-09-15 entries) and must not
repeat here

- `mm^2` vs `m^2`: factor of 1e6, not 1e3. A missed conversion here is a
  1,000,000x error, not a rounding error.
- `uOhm` vs `Ohm`: factor of 1e6.
- Area-normalized contact resistivity `uOhm.cm^2` vs `Ohm.m^2`: factor of
  1e-10 (see `uohm_cm2_to_ohm_m2` docstring for the derivation). **This
  specific conversion caused a real, converged-but-wrong 474K temperature
  rise in the personal-mentor project's Case 6 busbar** when an
  area-normalized contact value calibrated for one contact area was reused
  unscaled on a much smaller one. The lesson generalizes: an
  area-normalized property is not portable between geometries without
  explicit area accounting -- always compute `R_actual = R_area_normalized
  / contact_area`, print it, and sanity-check the resulting power
  (`I^2 * R_actual`) before trusting a converged solve.

## Temperature-dependent resistivity sign convention

```
rho_e(T) = rho_e,ref * (1 + alpha * (T - T_ref))
```

- `T` and `T_ref` are both in **kelvin**.
- `T_ref` is the temperature AT WHICH `rho_e,ref` and `alpha` were measured
  (293.15 K / 20 C for the values in `data/materials.csv`), not 0 C. Some
  published tables reference alpha at 0 C (273.15 K) instead -- **do not
  mix conventions**; if a new source's alpha is referenced at a different
  T_ref, convert it before adding it to `materials.csv`, and note the
  conversion in that row's `notes` field.
- This is a **linear** model. It is a good approximation for copper and
  aluminium over roughly 0-200 C. Extrapolating it to very high
  temperatures can and does predict zero or negative resistivity (a
  physically impossible result) -- clamp the valid range explicitly in any
  code that walks temperature far from the calibration range, and treat a
  negative or non-finite `rho_e(T)` as a hard error (see
  `materials.sigma_e`), never as a silently-continued computation.

## Current and voltage direction convention

- Current `I` is defined positive flowing from the "high" terminal
  (voltage/potential-reference or current-injection terminal, depending on
  the case's boundary-condition choice) to the "low"/ground terminal.
- Voltage drop is always reported as `V_drop = V_high_terminal -
  V_low_terminal` (or `V(current-carrying terminal) - V(ground terminal)`
  when the BC is current-specified on one end), so `V_drop >= 0` for a
  consistent current direction. A negative reported `V_drop` in any script
  or STAR-CCM+ report is a sign-convention bug, not a valid result -- flag
  it, do not silently take an absolute value.

## Ambient / reference temperature convention

- `T_inf` (ambient/fluid far-field) and `T_sur` (radiative surroundings)
  are tracked as separate symbols even when a case sets them equal
  numerically, because B02/B12/B30 explicitly allow them to differ
  (e.g. an enclosure with hot internal surroundings but cooler forced
  ventilation air).

## Result-status labels (roadmap Section 2 and 12)

Every numeric result anywhere in this repository (code output, CSV,
README, plot caption, progress-log entry) must carry exactly one of these
labels, and the label must not be dropped when the number is copied
elsewhere:

| Label | Meaning |
|---|---|
| `REFERENCE` | A published/authoritative value (textbook, standard, vendor datasheet, public solver documentation). Not computed by us. |
| `TARGET` | A value this program is trying to reproduce or bound (e.g. the COMSOL busbar's published ~330.42 K). |
| `ASSUMED` | A value we chose because the authoritative source did not specify it; must include a stated rationale and, where the acceptance gate requires it, a sensitivity sweep. |
| `SIMULATED` | A value produced by running our own analytical script or STAR-CCM+ case. |
| `PENDING` | A value not yet computed/run. Must never be presented alongside a plausible-looking number -- write the literal string `PENDING`, not a placeholder like `0` or `TBD` that could be mistaken for a real result. |
| `VERIFIED` | A `SIMULATED` result that has passed its stated internal conservation/mesh gate. |
| `VALIDATED` | A `VERIFIED` result that has also been compared against independent `REFERENCE` data within a stated, justified tolerance. |

`VERIFIED` and `VALIDATED` are earned status upgrades, not initial labels --
a result starts as `SIMULATED` and only becomes `VERIFIED`/`VALIDATED`
after the specific gate in the roadmap's Section 3 table is checked and
passes, with the check itself recorded in `docs/validation_matrix.md`.
