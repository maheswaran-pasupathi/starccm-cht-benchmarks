# STAR-CCM+ CHT Benchmarks

Conjugate heat transfer (CHT) studies built in STAR-CCM+, using real published
open-access data as model input, and validated against closed-form analytical
solutions where one exists. No proprietary or employer data anywhere in this
repository -- every input number is either a public physical constant, a
cited literature value, or an original design choice made for this project.

## Case 1: Air-cooled 18650 cell, forced cross-flow

A single 18650 cylindrical Li-ion cell, standing in a forced-air duct,
generating heat uniformly through its volume and losing it by convection to
the surrounding air -- a simplified, single-cell version of the kind of
problem a battery-pack thermal design has to solve many times over.

### Real input data (not invented), with sources

| Quantity | Value used | Source |
|---|---|---|
| Cell form factor | 18 mm diameter x 65 mm height | Industry-standard 18650 dimensions |
| Volumetric heat generation | 41,789 W/m³ at 2C discharge | Literature CFD study on 18650 thermal characterization |
| Specific heat | ~1000 J/kg·K | Measured, Sony US18650 cell, 960-1040 J/kg·K range |
| Radial thermal conductivity | ~0.2-0.43 W/m·K (literature range; 0.30 used) | Multiple sources; 18650 cells are strongly anisotropic -- radial conductivity is 1-2 orders of magnitude below axial |
| Density | ~2720 kg/m³ | Computed from a typical commercial 18650 cell mass (~45 g) and the real cell volume |

The radial conductivity is used **isotropically** as a deliberate
simplification (it is the dominant path for heat leaving through the cooled
lateral surface in this geometry) -- stated here, not hidden.

### Validation approach

Rather than trying to match one paper's specific reported temperature curve
(a fragile target — every paper's exact geometry, coolant, and boundary
conditions differ), this case validates the *physics*, using two independent
checks:

1. **Total heat rate sanity check** -- the CFD's own computed total heat
   leaving the cell should equal `q''' x cell volume` exactly, since that is
   simply energy conservation, not a modeling assumption.
2. **Closed-form conduction check** -- for steady radial conduction with
   uniform volumetric generation in a solid cylinder, insulated end caps, the
   center-to-surface temperature rise has an exact analytical solution
   (Incropera & Bergman, *Fundamentals of Heat and Mass Transfer*, a standard
   textbook result):

   ```
   T_center - T_surface = q''' R² / (4k)
   ```

   The macro computes this directly from the CFD's own surface temperature
   and the cell's k, R, and q''', and reports the CFD-vs-analytical
   difference. This checks that STAR-CCM+'s solid energy solve reproduces
   known, public-domain conduction physics correctly -- independent of any
   single paper's specific results.

## Status

**Stage 1 (done): pipeline verified working end to end.** CAD (block + cell
cylinder, boolean subtract), meshing (both regions, prism layers on the
cell), region/continuum setup, the air-cell CHT interface, 800-iteration
run, and the report/CSV/analytical-comparison logic all run cleanly and
produce a converged, physically sane result -- confirmed by a live run:
total heat exactly matches `q''' x volume` (0.691 W), and residuals
converge (flow residuals to machine-zero; energy to ~0.085 and still
declining -- see the open item below).

**A real bug was found and fixed in this stage**: the macro originally never
created the CHT interface between the air and the cell -- the cell's heat
source had nowhere to go. This was caught live: the energy residual sat
completely flat for 40+ iterations while flow residuals converged normally.
Fixed by adding the missing `createDirectInterface()` call.

**Stage 2 (next): real material properties.** This run used STAR-CCM+'s
default placeholder solid material, not yet the cell's real cited
properties above -- which is exactly why the validation check currently
shows a large (~100%) discrepancy: a near-isothermal cell (0.006 K rise)
is the expected result of a much-higher-conductivity placeholder material,
not a simulation error. Setting the real k/ρ/cp values (already computed
and ready to use, see the table above) is the next concrete step, expected
to close this gap substantially.

**Also open:** the energy residual, while genuinely converging (not stuck),
had not reached a tight final value within the 800-iteration budget used for
this first run -- more iterations, or solver/relaxation tuning, is worth
revisiting once the real material properties are in place.

## Running it

```
starccm+ -new -batch macros/cell18650_forced_air_cht.java
```

Parametric via environment variables:
- `CELL_U` -- duct inlet air velocity, m/s (default 2.0)
- `CELL_OUT` -- output CSV path (default `results/u_<U>.csv`)

Results write to `results/*.csv`. STAR-CCM+ `.sim` state files are not
committed (see `.gitignore`) -- they're large, machine-specific binaries;
the real results are the CSVs.
