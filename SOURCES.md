# Source Register

This file records the provenance of numerical inputs used by the STAR-CCM+ CHT benchmarks. The intent is to keep **literature-derived data**, **manufacturer data**, **derived values**, and **project assumptions** visibly separate.

## Case 1 — Air-cooled 18650 cell, forced cross-flow

### S1 — Specific heat of a Sony US-18650 cell

H. Maleki, S. Al Hallaj, J. R. Selman, R. B. Dinwiddie, H. Wang, “Thermal Properties of Lithium-Ion Battery and Components,” *Journal of The Electrochemical Society*, 146(3), 947–954, 1999.

- DOI: https://doi.org/10.1149/1.1391704
- Reported whole-cell heat capacity: 0.96 ± 0.02 J/g·K at 2.75 V OCV and 1.04 ± 0.02 J/g·K at 3.75 V OCV.
- Benchmark value: **1000 J/kg·K**, a rounded engineering value between those measurements.

### S2 — Volumetric heat generation at 2C

P. D. Dhabarde, R. P. Soni, J. G. Suryawanshi, “Passive-cooling of Li-ion batteries using PCM-metal foam in honeycomb enclosure,” *Applied Thermal Engineering*, 281, 128543, 2025.

- DOI: https://doi.org/10.1016/j.applthermaleng.2025.128543
- Public preprint: https://papers.ssrn.com/sol3/papers.cfm?abstract_id=5366050
- Reported modelling condition: **41,789 W/m³** uniform volumetric heat generation at **2C**.
- Benchmark value: **41,789 W/m³**.

### S3 — Radial thermal conductivity of cylindrical Li-ion cells

Caltech Undergraduate Research Journal, “Radial Thermal Conductivity Measurements of Lithium-Ion Battery Cells,” 2020.

- Public page: https://curj.caltech.edu/2020/06/20/radial-thermal-conductivity-measurements-of-lithium-ion-battery-cells/
- Reported effective radial conductivity for the tested 18650 cell: **0.43 ± 0.07 W/m·K**.

N. S. Spinner et al., “Novel 18650 lithium-ion battery surrogate cell design with anisotropic thermophysical properties for studying failure events,” *Journal of Power Sources*, 312, 2016.

- Public repository record: https://digitalcommons.unl.edu/usnavyresearch/104/
- Reported radial conductivity: **0.120–0.197 W/m·K**.
- Reported axial conductivity: **5.1 ± 0.6 W/m·K**.
- Benchmark value: **0.30 W/m·K**, deliberately selected between published radial measurements and used isotropically for this first transparent analytical benchmark. It is a project modelling choice, not a direct measurement of the Samsung cell used for the envelope/mass reference.

### S4 — Commercial 18650 envelope and mass reference

Samsung SDI INR18650-25R product specification.

- Publicly mirrored specification: https://www.powerstream.com/p/INR18650-25R-datasheet.pdf
- Manufacturer specification values: diameter **18.33 ± 0.07 mm**, height **64.85 ± 0.15 mm**, mass **45.0 g max** (technical data also show a typical mass around 43.8 g).
- Benchmark geometry: **18 mm × 65 mm**, rounded to the nominal 18650 envelope.
- Density in the macro is **derived**, not independently measured: 0.045 kg divided by the benchmark cylindrical volume, giving approximately **2720 kg/m³**.

### S5 — Analytical validation equation

For a constant-property solid cylinder with uniform volumetric heat generation, steady one-dimensional radial conduction and the usual symmetry condition at the centerline, the center-to-surface temperature rise is:

```text
T_center - T_surface = q''' R² / (4 k)
```

This is a standard internal-heat-generation solution of the cylindrical heat equation; see heat-transfer textbooks such as F. P. Incropera et al., *Fundamentals of Heat and Mass Transfer*.

## Provenance policy

For future cases, every numerical input should be tagged as one of:

1. **Measured literature value** — experimentally reported in a cited source.
2. **Published modelling input** — used as a boundary condition/property in a cited study, but not necessarily measured there.
3. **Manufacturer specification** — taken from a product specification/datasheet.
4. **Derived value** — calculated transparently from cited quantities.
5. **Project assumption/design choice** — selected for the benchmark and explicitly labelled as such.

Do not describe a selected representative value as though it were measured for the same physical cell unless the cited source actually establishes that provenance.
