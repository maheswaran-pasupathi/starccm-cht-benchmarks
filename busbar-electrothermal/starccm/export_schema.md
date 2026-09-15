# STAR-CCM+ export schema

Every B10+ run writes one CSV row to `results/processed/<case_id>_results.csv`
with (at minimum) the scalar columns the roadmap's Section 7 requires,
narrowed to what's actually meaningful for an electrical-only case
(thermal/mesh-count columns that don't apply to a given case are included
as empty/NA rather than omitted, so every case's CSV has the same header).

## Common columns (all B10+ cases)

```
case_id, run_id, starccm_version, run_date, git_commit_hash,
geometry_length_m, geometry_width_m, geometry_thickness_m, geometry_area_m2,
material, material_source_id,
mesh_cell_count, mesh_base_size_m,
imposed_current_A, terminal_voltage_prescribed_V,
current_Iout_A, current_Vin_A, current_imbalance_pct,
voltage_Iout_V, voltage_Vin_V, voltage_drop_V,
power_JdotE_integral_W, power_I_times_V_W, power_mismatch_pct,
voltage_drop_vs_B01_pct, power_vs_B01_pct,
Jmax_A_m2, J_section_avg_A_m2, J_nominal_I_over_A_A_m2, J_section_avg_vs_nominal_pct,
Tmax_K, T_avg_K,
convergence_status, gate_status
```

Columns not applicable to a given case (e.g. `Tmax_K` for a B10
electrical-only run) are written as the literal string `NA`, never `0` or
blank (an empty cell is easy to misread as a missed export; `NA` is
unambiguous).

## Mandatory images per run (roadmap Section 7)

Saved to `results/figures/<case_id>_<description>.png`:

1. `<case_id>_geometry.png` — geometry with named boundaries visible.
2. `<case_id>_mesh_overview.png` + `<case_id>_mesh_terminal_closeup.png`.
3. `<case_id>_potential.png` — electric potential field.
4. `<case_id>_current_density.png` — current-density magnitude, with the
   section-sampling plane visible where applicable.
5. `<case_id>_convergence.png` — residual/monitor history.

## Camera-framing note (carried forward, a real lesson from this session)

Passing a `Region` object directly to a `PartDisplayer`/`ScalarDisplayer`
renders nothing visible — always pass the `Region`'s boundary list
(`region.getBoundaryManager().getBoundaries()`) instead. And
`scene.open(true)` alone does not frame a long thin part usefully — set
an explicit `VisView` (`focal`, `position`, `up`, `parallelScale`) sized to
the part's actual bounding box. Both found live in personal-mentor
`hubbell_cae` Case 3 (RUN_LOG.md, 2026-09-15).
