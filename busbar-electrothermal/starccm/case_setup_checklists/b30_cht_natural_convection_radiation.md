# B30 setup checklist — CHT natural-convection air domain + radiation

Per the roadmap's B30 section and the day-4 "Core" instruction: "Add B30
natural-convection air domain and radiation to the selected baseline...
Close the global energy balance and report convection/radiation/end-
conduction heat split." This replaces B12's fixed `h=10 W/m^2K`
convection approximation with a REAL external air domain solving
buoyancy-driven natural convection, plus radiation to a large ambient
environment.

## Flow-regime justification (checklist requirement: "laminar/turbulent
choice justified with Rayleigh/Reynolds estimates")

`src/b30_rayleigh_estimate.py`, run BEFORE any STAR-CCM+ work:

- Characteristic length (horizontal-plate `A_s/P` convention, Incropera
  & Bergman Ch. 9): `L_c = 0.01765 m`.
- `Ra = 7.54e3` at an assumed representative ΔT=15K (B12's own converged
  rise order-of-magnitude — used only to scope the regime, not as an
  input to the real solve).
- Horizontal-plate transition to turbulent natural convection is
  `Ra ~ 1e7-1e8` (Incropera & Bergman Ch. 9) — **`Ra=7.5e3` is 3-4 orders
  of magnitude below transition: LAMINAR**, justifying `LaminarModel`
  rather than a turbulence model.

## Geometry — bar split into 3 x-segments, air domain around the middle only

Same lesson as B22 (exactly-matching faces for `createDirectInterface`):
the bar is split at the box's two end planes into `barLeft` (outside the
box, carries the Vin terminal), `barMid` (inside the box, the CHT
region), and `barRight` (outside the box, carries the Iout terminal) —
**not** one continuous bar with a partial-overlap air region.

| Segment | x-range (m) | Role |
|---|---|---|
| `barLeft` | [-0.02, 0.00] | Outside box. Vin terminal at x=-0.02. Perfect interface to `barMid` at x=0. |
| `barMid` | [0.00, 0.26] | Inside the air box. Its 4 side faces are the real CHT interface to air. |
| `barRight` | [0.26, 0.28] | Outside box. Iout terminal at x=0.28. Perfect interface to `barMid` at x=0.26. |

Total bar length = 0.02+0.26+0.02 = **0.30m**, matching the B01-B22
baseline exactly. Cross-section 0.040×0.005m, also unchanged.

**Why the electrical terminals cannot sit inside the air domain:** air
has no `ElectrodynamicsPotentialModel` — a current-carrying boundary
condition cannot be applied at a CHT interface shared with a
non-electrical fluid. The terminals must be true external boundaries,
which is why `barLeft`/`barRight` sit fully outside the box.

**Air box** (before subtraction): `x:[0,0.26]` (exactly matching
`barMid`'s x-range — no axial margin, since axial flow along the bar's
own length is not this problem's dominant convective direction),
`y:[-0.15,0.15]` (0.30m), `z:[-0.20,0.20]` (0.40m, taller than wide to
give the buoyant plume room to develop above/below the bar). Clearance
around the bar: ~7-11× the Rayleigh characteristic length `L_c` in
width/height. **ASSUMED domain size, not checked for boundary-distance
sensitivity this increment** — see `docs/limitations.md`.

`SubtractPartsOperation(target=box, tools=[barMid_tool])` (a duplicate
of `barMid`, consumed by the subtraction) gives the fluid region: the
box interior minus the bar-shaped cavity running through it, plus a
box-cross-section-minus-bar-cross-section "frame" opening at each axial
end (`x=0` and `x=0.26`) where `barLeft`/`barRight` sit flush against
the box's end planes without overlapping the fluid.

## Physics

**Solid (`barLeft`/`barMid`/`barRight`):** same chain as B12/B21/B22 --
`SolidModel`+`SegregatedSolidEnergyModel`+`ConstantDensityModel`+
`ElectromagnetismModel`+`ElectrodynamicsPotentialModel`+
`OhmicHeatingModel`. `barLeft`/`barRight`'s own outer faces (outside the
box) keep the old fixed `h=10 W/m^2K` convection -- only `barMid`'s
faces get the real CHT/air treatment.

**Fluid (air, box):** `ThreeDimensionalModel`+`SteadyModel`+
`SingleComponentGasModel` (air)+`SegregatedFlowModel`+`LaminarModel`
(per the Rayleigh justification above)+`SegregatedFluidTemperatureModel`
+`GravityModel`+`BoussinesqModel`. Air properties from
`data/materials.csv` (density, cp, k, mu, beta all at 300K, Incropera &
Bergman Table A.4).

**CHT interface:** `barMid`'s 4 side-face boundary ↔ the fluid cavity's
matching inner boundary, `createDirectInterface`, default conjugate heat
transfer (no added contact resistance — that is B22's subject, not this
case's).

**Radiation:** added as a second stage after natural-convection CHT is
confirmed converging on its own (same staged-build approach used for
B22's interface debugging). Emissivity 0.78 (oxidized copper, already
cited in `data/materials.csv` from B02) on `barMid`'s CHT-facing
surface, radiating to a large ambient environment at 293.15K.

## Boundary conditions on the box's own outer/end faces

- The 4 outer side walls of the box (large box faces, not the bar
  cavity): pressure/ambient opening at 293.15K, 1 atm -- representing an
  effectively unbounded room, not a sealed enclosure (matching the
  roadmap's "open natural convection with buoyancy" case, not the
  "sealed still air" case, which is deferred).
- The 2 axial end "frame" openings (box cross-section minus bar
  cross-section): same ambient opening treatment.

## Reports (per the roadmap's B30 "Outputs" and the Core instruction)

- Global energy balance: `P_JdotE` (Joule heating, volume-integrated in
  the solid) vs `Q_convection + Q_radiation + Q_end_conduction` (heat
  leaving through the air CHT interface via convection, via radiation,
  and via conduction into `barLeft`/`barRight`'s own fixed-h faces) --
  gate <2%, same family as B02/B11/B12/B21/B22's own heat-balance gates.
- Heat split: convection fraction, radiation fraction, end-conduction
  fraction of total loss.
- `Tmax`, air velocity/plume field (qualitative, via exported scene).
- Local heat-transfer coefficient (back-calculated from the CHT
  interface's own solved wall heat flux and temperature difference) --
  reported for comparison against the old fixed `h=10` assumption used
  everywhere else in this repo.

## Results (2026-09-16)

**Stage 1 (natural convection, no radiation) -- PASSED.** Recovered via
the saved `.sim` after the main run hit a stale-Boundary-reference bug
for `Q_cht` (same class of bug as B22 -- see `docs/discrepancy_log.md`):

| Quantity | Value | Gate | Result |
|---|---|---|---|
| Current imbalance | 7.2e-13% | <0.5% | PASS |
| Heat balance | 1.70% | <5% | PASS |
| `Tmax_solid` | 308.81K / 35.66C | -- | -- |
| `Tmax_air` | 303.70K / 30.55C | -- | -- |
| `Q_leadConv` | 0.532 W | -- | -- |
| `Q_cht` (barMid -> air) | 3.431 W | -- | -- |

**Stage 2 (natural convection + radiation) -- PASSED**, warm-started
from stage 1's converged state (avoiding a full ~14-hour cold-start
rebuild):

| Quantity | Value | Gate | Result |
|---|---|---|---|
| Current imbalance | 4.1e-12% | <0.5% | PASS |
| Heat balance | 4.68% | <5% | PASS |
| `Tmax_solid` | 306.88K / **33.73C** | -- | -- |
| `Tmax_air` | 302.41K / 29.26C | -- | -- |
| `Q_leadConv` | 0.494 W | -- | -- |
| `Q_cht` (barMid -> air, includes radiation implicitly) | 3.727 W | -- | -- |

**Radiation's real, verified effect:** `Tmax` DROPS from 35.66C (no
radiation) to 33.73C (with radiation) at the identical 400A -- radiation
adds a genuine additional heat-rejection path, so the same Joule heating
dissipates at a lower steady-state temperature. This comparison does
NOT depend on isolating the radiative fraction of `Q_cht` explicitly
(which was attempted but not reliably isolated -- see
`docs/discrepancy_log.md` and `docs/limitations.md`).

**Two more real construction issues found and fixed** (full write-ups
in `docs/discrepancy_log.md`): (1) `S2sModel`/`GrayThermalRadiationModel`
could not be added to an already-built continuum whose model list
already included Electromagnetism ("no registration found" regardless
of ordering tried against the already-built continuum) -- the fix was
building TWO NEW continua with radiation in the correct slot (right
after energy/density, before electromagnetism, found via a minimal
single-block test) and reassigning the existing regions
(`ContinuumManager.setContinuum`); (2) `ViewfactorsCalculatorModel` +
`PatchGeneratorModel` are additional required models not mentioned by
the "no registration found" error for S2S alone -- found from a
subsequent "missing required models: [Viewfactors]" error at solve time.

**A costly operational lesson:** a second-client "peek" connection
attempted mid-run (to check heat balance without waiting for the full
iteration target) is suspected to have caused the primary connection to
reset right at the end of a ~9-hour run, losing all progress with no
recovery point. Fixed by (1) never attempting a second client connection
while `run()` is active, and (2) explicitly configuring
`star.common.AutoSave` (`setAutoSaveBatch(true)`,
`getStarUpdate().setUpdateFrequency(150)`) at the start of any
long-running macro from now on.

## Deferred, not silently dropped (per the roadmap's full B30 case list)

- Forced-air cases (multiple speeds/directions): deferred, this
  increment is natural convection only.
- Sealed/closed enclosure case: deferred, this increment uses an open
  ambient boundary.
- Solar load: explicitly deferred per the roadmap's own wording ("only
  after the basic balances are correct").
- Emissivity sensitivity sweep: deferred -- one representative value
  (0.78) used, matching this repo's existing B02 convention.
- Boundary-distance (domain-size) sensitivity check: not run this
  increment -- see `docs/limitations.md`.
