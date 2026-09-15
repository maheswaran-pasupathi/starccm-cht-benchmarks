# STAR-CCM+ CHT Benchmarks

Conjugate heat transfer (CHT) studies built in STAR-CCM+, using publicly
traceable literature/manufacturer data as model input and validated against
closed-form analytical solutions where one exists. No proprietary employer
data are used anywhere in this repository. Every input is classified as one
of: **published measurement**, **manufacturer specification**, **published
modelling input**, **public physical constant**, or **original project
assumption/design choice**.

## Case 1: Air-cooled 18650 cell, forced cross-flow

A single 18650 cylindrical Li-ion cell, standing in a forced-air duct,
generating heat uniformly through its volume and losing it by convection to
the surrounding air -- a simplified, single-cell version of the kind of
problem a battery-pack thermal design has to solve many times over.

### Input data and provenance

| Quantity | Value used | Classification | Source / rationale |
|---|---:|---|---|
| Cell geometry | 18 mm diameter × 65 mm height | Project geometry based on commercial 18650 envelope | Samsung SDI INR18650-25R specification gives 18.33 ± 0.07 mm diameter and 64.85 ± 0.15 mm height; the benchmark rounds this to the nominal 18 × 65 mm 18650 envelope. |
| Cell mass used for density | 45 g | Manufacturer specification | Samsung SDI INR18650-25R product specification: cell weight 45.0 g max (typical value reported separately as ~43.8 g in the technical data). |
| Effective density | ~2720 kg/m³ | Derived project value | Calculated from 45 g divided by the benchmark's 18 mm × 65 mm cylindrical volume. It is therefore not an independently measured density. |
| Volumetric heat generation | 41,789 W/m³ at 2C | Published modelling input | P. D. Dhabarde, R. P. Soni, J. G. Suryawanshi, *Passive-cooling of Li-ion batteries using PCM-metal foam in honeycomb enclosure*, Applied Thermal Engineering 281 (2025) 128543, DOI: 10.1016/j.applthermaleng.2025.128543. The same 2C / 41,789 W/m³ value appears in the authors' publicly available preprint: *A Novel Approach to Passive Thermal Management of Li-Ion Batteries Using Phase Change Materials Embedded in Porous Metal Foam within a Honeycomb-Structured Enclosure*, SSRN (2025), abstract_id=5366050. |
| Specific heat | 1000 J/kg·K | Published measurement, rounded | H. Maleki, S. Al Hallaj, J. R. Selman, R. B. Dinwiddie, H. Wang, *Thermal Properties of Lithium-Ion Battery and Components*, Journal of The Electrochemical Society 146(3) (1999) 947–954, DOI: 10.1149/1.1391704. Sony US-18650 measurements: 0.96 ± 0.02 J/g·K at 2.75 V OCV and 1.04 ± 0.02 J/g·K at 3.75 V OCV. The benchmark uses the midpoint/rounded engineering value 1.00 J/g·K = 1000 J/kg·K. |
| Radial thermal conductivity | 0.30 W/m·K | Project value selected inside published 18650 range | Direct 18650 measurements reported by the Caltech undergraduate study *Radial Thermal Conductivity Measurements of Lithium-Ion Battery Cells* give 0.43 ± 0.07 W/m·K for the tested 18650 cell. N. S. Spinner et al., *Novel 18650 lithium-ion battery surrogate cell design with anisotropic thermophysical properties for studying failure events*, Journal of Power Sources 312 (2016), reports radial values of 0.120–0.197 W/m·K and axial conductivity 5.1 ± 0.6 W/m·K. The benchmark deliberately chooses 0.30 W/m·K as a representative effective radial value between these published measurements; it is **not claimed to be a measured property of the Samsung cell above**. |
| Solid-property treatment | k = 0.30 W/m·K isotropic | Explicit modelling simplification | Real cylindrical cells are anisotropic because of the jelly-roll structure. This first benchmark uses the representative radial value isotropically so the analytical radial-conduction check stays transparent. A later benchmark should use an orthotropic solid model. |
| Inlet temperature | 300 K | Project boundary condition | Chosen benchmark operating condition, not literature-derived. |
| Inlet velocity | 2.0 m/s default | Project boundary condition | Chosen baseline; exposed through `CELL_U` for parametric sweeps. |

### Primary references

1. **Maleki, H.; Al Hallaj, S.; Selman, J. R.; Dinwiddie, R. B.; Wang, H.**
   “Thermal Properties of Lithium-Ion Battery and Components.” *Journal of
   The Electrochemical Society*, 146(3), 947–954, 1999.
   DOI: **10.1149/1.1391704**.

2. **Dhabarde, P. D.; Soni, R. P.; Suryawanshi, J. G.**
   “Passive-cooling of Li-ion batteries using PCM-metal foam in honeycomb
   enclosure.” *Applied Thermal Engineering*, 281, 128543, 2025.
   DOI: **10.1016/j.applthermaleng.2025.128543**.
   Public preprint version: SSRN abstract **5366050**.

3. **Spinner, N. S. et al.** “Novel 18650 lithium-ion battery surrogate cell
   design with anisotropic thermophysical properties for studying failure
   events.” *Journal of Power Sources*, 312, 2016. Reported axial thermal
   conductivity: 5.1 ± 0.6 W/m·K; reported radial range: 0.120–0.197 W/m·K.

4. **Caltech Undergraduate Research Journal.** “Radial Thermal Conductivity
   Measurements of Lithium-Ion Battery Cells,” 2020. Reported effective
   radial thermal conductivity for the tested 18650 cell: **0.43 ± 0.07
   W/m·K**.

5. **Samsung SDI INR18650-25R product specification.** Cell envelope:
   **18.33 ± 0.07 mm diameter × 64.85 ± 0.15 mm height**; mass **45.0 g max**.
   These values are used only to establish a realistic commercial 18650
   envelope/mass scale; the thermal-property sources above refer to other
   18650 cells and are not represented as Samsung 25R measurements.

> **Source-integrity note:** this case intentionally combines values from
> different published 18650 sources to construct a transparent *physics
> benchmark*. It is not intended to reproduce one specific commercial cell.
> Wherever a value is selected rather than directly measured for the same
> cell, that fact is stated explicitly.

### Validation approach

Rather than trying to match one paper's specific reported temperature curve
(a fragile target because every paper's exact geometry, coolant, material
model, and boundary conditions differ), this case validates the *physics*
using two independent checks:

1. **Total heat rate sanity check** -- the CFD-computed total heat leaving the
   cell should equal `q''' × cell volume`, since this follows directly from
   steady energy conservation.
2. **Closed-form conduction check** -- for steady radial conduction with
   uniform volumetric generation in a solid cylinder with insulated end caps,
   the center-to-surface temperature rise is

   ```text
   T_center - T_surface = q''' R² / (4k)
   ```

   This is the standard constant-property cylindrical heat-conduction result
   obtained from the steady heat equation with uniform volumetric generation
   (see, e.g., Incropera et al., *Fundamentals of Heat and Mass Transfer*,
   internal heat-generation solutions for a cylinder).

   The macro computes the expression directly from the CFD case's k, R and
   q''' and compares it with the CFD center-to-surface temperature rise. This
   checks whether STAR-CCM+'s solid-energy solution reproduces the expected
   radial-conduction physics independent of a particular battery paper's
   temperature curve.

## Status

**Stage 1 (done): pipeline verified working end to end.** CAD (block + cell
cylinder, boolean subtract), meshing (both regions, prism layers on the
cell), region/continuum setup, the air-cell CHT interface, 800-iteration
run, and the report/CSV/analytical-comparison logic all run cleanly and
produce a converged, physically sane result -- confirmed by a live run:
total heat exactly matches `q''' × volume` (0.691 W), and residuals
converge (flow residuals to machine-zero; energy to ~0.085 and still
declining -- see the open item below).

**A real bug was found and fixed in this stage**: the macro originally never
created the CHT interface between the air and the cell -- the cell's heat
source had nowhere to go. This was caught live: the energy residual sat
completely flat for 40+ iterations while flow residuals converged normally.
Fixed by adding the missing `createDirectInterface()` call.

**Stage 2 (done): real material properties.** The Stage 1 run used
STAR-CCM+'s default placeholder solid material, which is why the
validation check showed a large (~100%) discrepancy: a near-isothermal
cell (0.006 K rise) was the expected result of a much-higher-conductivity
placeholder, not a simulation error. The macro now sets the cell's cited
k/ρ/cp values directly on the material -- found via
`star.material.MaterialProperty.setConstant(double)`, reached through the
continuum's `SolidModel.getMaterial()` and
`MaterialPropertyManager.getMaterialProperty(Class)`, using the
per-property classes `star.flow.ConstantDensityProperty`,
`star.energy.SpecificHeatProperty`, and
`star.energy.ThermalConductivityProperty`. Compiled clean against the
real STAR-CCM+ jars. Next: rerun with the documented properties in place
and confirm the analytical-vs-CFD gap closes substantially.

**Also open:** the energy residual, while genuinely converging (not stuck),
had not reached a tight final value within the 800-iteration budget used for
this first run -- more iterations, or solver/relaxation tuning, is worth
revisiting once the documented material properties are in place.

## Running it

```bash
starccm+ -new -batch macros/cell18650_forced_air_cht.java
```

Parametric via environment variables:

- `CELL_U` -- duct inlet air velocity, m/s (default 2.0)
- `CELL_OUT` -- output CSV path (default `results/u_<U>.csv`)

Results write to `results/*.csv`. STAR-CCM+ `.sim` state files are not
committed (see `.gitignore`) -- they're large, machine-specific binaries;
the reproducible outputs are the CSVs and the documented case definition.
