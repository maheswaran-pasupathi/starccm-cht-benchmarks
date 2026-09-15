# B20 setup checklist — voltage-driven proxy (simplified reproduction)

**Read `docs/b20_comsol_audit.md` first.** The roadmap's cited COMSOL
reference (160 A / 330.42 K) could not be verified in either official
COMSOL busbar tutorial. This case does **not** attempt to reproduce that
figure. Instead it reproduces the *methodology* of the real, verified
`busbar_llac` tutorial — a **voltage-driven** (not current-driven)
electro-thermal Joule-heating solve — on our own existing straight-bar
geometry (same as B10/B11/B12), so the two are directly comparable to our
own current-driven B12 result.

## What this case is, and is not

- **Is:** an electrical/heat-balance methodology check — does a
  voltage-BC coupled electro-thermal solve conserve current and energy
  correctly, same gates as B12, on the same geometry.
- **Is not:** a reproduction of the real COMSOL tutorial's geometry
  (3-bolt L-bracket, not a straight bar), materials (copper+titanium, not
  copper-only), or `Tmax` (its reported temperatures depend on that
  different geometry's surface area and cannot be matched by a different
  shape). Per the roadmap's own warning, do not call `Tmax` here
  "validated" against the published figure.

## Physics — same chain as B12

1. `ThreeDimensionalModel`, `SteadyModel`, `SolidModel`
2. `SegregatedSolidEnergyModel` (before `ConstantDensityModel`)
3. `ConstantDensityModel`
4. `ElectromagnetismModel` (explicit parent)
5. `ElectrodynamicsPotentialModel`
6. `OhmicHeatingModel`

## Boundary conditions — VERIFIED values from `busbar_llac` (see audit doc)

- `Vin` face: `ElectrodynamicsPotentialWallOption.ELECTRIC_POTENTIAL` =
  **0.020 V** (20 mV, matches the real tutorial's upper-right bolt BC).
- `Iout` face: `ElectrodynamicsPotentialWallOption.ELECTRIC_POTENTIAL` =
  **0.0 V** (ground, matches the real tutorial's lower-bolt BC). Note this
  is a change from B10/B12, which used a prescribed 400 A current BC here
  — B20 is voltage-driven on BOTH terminals, current is a solved output,
  not a specified input.
- Four side faces: convection, `h = 10 W/m^2K` (same generic value as
  B02/B11/B12 — the real tutorial's own `htca` is in an external
  parameter file we do not have, so this stays our own documented
  `ASSUMED` value, not silently matched to an unknown source value),
  `T_inf = 293.0 K` (VERIFIED from the real tutorial's stated ambient,
  used in place of our usual 293.15K reference convention for this one
  case so the BC matches its cited source exactly — a 0.15K difference is
  immaterial to any gate here).
- Terminal end-caps: adiabatic (same as B12).

## Expected result differs from B12 in magnitude, by design

B12 prescribes 400 A and solves for ~10 mV drop. B20 prescribes 20 mV and
solves for whatever current that implies on OUR bar's resistance
(`R = 25.2 uOhm` from B01) — expect roughly **I = V/R ~ 793 A**, i.e.
a *larger* current than B12 for a *smaller* voltage, because our bar's
resistance is much lower than the real tutorial's actual (unmodeled)
bracket resistance. This is expected and documented, not a bug: the real
tutorial's 20 mV drives its own bracket geometry's resistance, not ours.

## Validation hierarchy actually checked (roadmap B20 items 1–3 only)

1. **BC/methodology audit** — confirmed via `docs/b20_comsol_audit.md`
   before this macro was written.
2. **Electrical power/voltage checks** — `P_JdotE` vs `I*V_drop` (same
   check as B12); current balance at the two terminals (same check as
   B10/B12, magnitude convention per `docs/units_and_sign_conventions.md`).
3. **Boundary heat-flow audit** — heat balance (applied Joule heat vs
   rejected convective heat), same check as B11/B12.

Items 4–6 (temperature-field pattern vs the published figure, `Tmax` and
its location vs a reference, mesh sensitivity vs a published value) are
explicitly NOT attempted here — see `docs/b20_comsol_audit.md` for why.

## Result (2026-09-15)

**PASSED** (all four gates), 3000 iterations, Energy residual 2.65e-6.

| Quantity | Value | Gate | Result |
|---|---|---|---|
| Current imbalance | 3.9e-13% | <0.5% | PASS |
| Voltage BC check (V_drop vs 20mV applied) | 3.5e-14% | <0.1% | PASS |
| Power mismatch (`J.E` vs `I*V`) | 2.8e-13% | <1% | PASS |
| Heat balance (`Q_sides` vs `P_JdotE`) | 0.676% | <2% | PASS |
| Solved current | 793.65 A | (order-of-magnitude check, ~793.65 A expected) | matches to machine precision |
| `Tmax` / `Tavg` | 351.40 K / 351.39 K (78.25 C / 78.24 C) | not gated — see below | n/a |

**On `Tmax` = 78.25 C:** this is NOT compared to the real tutorial's
published figure, and NOT claimed as a validation of anything against
COMSOL. It results entirely from applying the real tutorial's verified
20 mV/293 K boundary condition TYPE to a DIFFERENT geometry (our
0.300×0.040×0.005 m bar) with a much smaller convective surface area and
no titanium bolts — a large solved current (793.65 A, vs the real bracket's
unknown current under the same 20 mV) on that geometry naturally produces
a large temperature rise. The near-perfect gate numbers (1e-13 level)
confirm the coupled voltage-driven solve itself is numerically correct —
exactly the "electrical/heat-balance methodology" check this proxy was
built for — not that any absolute temperature matches a reference.

